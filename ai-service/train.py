"""Offline training and evaluation for the FraudShield XGBoost model."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

try:
    from .features import CATEGORICAL_FEATURES, ENCODED_FEATURES, MODEL_FEATURES, NUMERIC_FEATURES, encode_features
except ImportError:  # direct execution from the ai-service directory
    from features import CATEGORICAL_FEATURES, ENCODED_FEATURES, MODEL_FEATURES, NUMERIC_FEATURES, encode_features


RANDOM_SEED = 42
SPLIT_DATE = pd.Timestamp("2026-01-01T00:00:00+05:30")
HIGH_RISK_MCC = {"4829", "5967", "6051", "7995"}


def compute_baseline_score(row: pd.Series) -> int:
    """Rules-only AI baseline specified by the case study; not online R1–R13."""
    if int(row["velocity_10m"]) >= 5 or str(row["merchant_risk"]) == "BLACKLISTED":
        return 100
    amount, ratio = float(row["amount"]), row["amount_to_avg30_ratio"]
    points = 25 if int(row["velocity_10m"]) in (3, 4) else 0
    if (pd.notna(ratio) and float(ratio) > 5 and amount >= 10000) or (pd.isna(ratio) and amount >= 50000):
        points += 30
    if bool(row["is_international"]) and bool(row["country_risk_high"]):
        points += 25
    if str(row["mcc"]) in HIGH_RISK_MCC:
        points += 15
    if 0 <= int(row["local_hour"]) <= 4 and amount >= 20000:
        points += 15
    if str(row["channel"]) == "ECOM" and bool(row["new_merchant_flag"]) and amount >= 25000:
        points += 20
    return min(points, 100)


def _metrics(y_true: np.ndarray, probabilities: np.ndarray, threshold: float) -> dict[str, Any]:
    from sklearn.metrics import average_precision_score, confusion_matrix, f1_score, precision_score, recall_score, roc_auc_score

    predictions = (probabilities >= threshold).astype(int)
    return {
        "threshold": round(float(threshold), 4),
        "precision": float(precision_score(y_true, predictions, zero_division=0)),
        "recall": float(recall_score(y_true, predictions, zero_division=0)),
        "f1": float(f1_score(y_true, predictions, zero_division=0)),
        "roc_auc": float(roc_auc_score(y_true, probabilities)) if len(np.unique(y_true)) == 2 else None,
        "pr_auc": float(average_precision_score(y_true, probabilities)) if len(np.unique(y_true)) == 2 else None,
        "confusion_matrix": confusion_matrix(y_true, predictions).tolist(),
        "fraud_count": int(y_true.sum()),
        "row_count": int(len(y_true)),
    }


def _cost(y_true: np.ndarray, probabilities: np.ndarray, amounts: np.ndarray, threshold: float) -> float:
    predicted = probabilities >= threshold
    missed_fraud = (y_true == 1) & ~predicted
    false_alarm = (y_true == 0) & predicted
    return float(amounts[missed_fraud].sum() + 150 * false_alarm.sum())


def _cost_optimal_threshold(y_true: np.ndarray, probabilities: np.ndarray, amounts: np.ndarray) -> tuple[float, float]:
    candidates = np.linspace(0.01, 0.99, 99)
    threshold = min(candidates, key=lambda value: _cost(y_true, probabilities, amounts, float(value)))
    return float(threshold), _cost(y_true, probabilities, amounts, float(threshold))


def _encode(frame: pd.DataFrame) -> np.ndarray:
    return np.asarray([encode_features(row) for row in frame.loc[:, MODEL_FEATURES].to_dict("records")], dtype=np.float32)


def _out_of_time_split(data: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    data = data.assign(parsed_ts=pd.to_datetime(data["txn_ts"], utc=True))
    train_period = data[data["parsed_ts"] < SPLIT_DATE].sort_values("parsed_ts")
    test_period = data[data["parsed_ts"] >= SPLIT_DATE].sort_values("parsed_ts")
    if train_period.empty or test_period.empty:
        raise ValueError("data must include rows before and on/after 2026-01-01 for the required out-of-time split")
    validation_start = int(len(train_period) * 0.85)
    train, validation = train_period.iloc[:validation_start], train_period.iloc[validation_start:]
    if len(np.unique(train["is_fraud"])) != 2 or len(np.unique(validation["is_fraud"])) != 2 or len(np.unique(test_period["is_fraud"])) != 2:
        raise ValueError("each train, validation and test period must contain both classes")
    return train, validation, test_period


def _fairness(frame: pd.DataFrame, probabilities: np.ndarray, threshold: float) -> dict[str, Any]:
    result: dict[str, Any] = {}
    evaluated = frame.assign(_challenged=probabilities >= threshold)
    genuine = evaluated[evaluated["is_fraud"] == 0]
    for dimension in ("city_tier", "card_type"):
        groups: dict[str, float | None] = {}
        for value, group in genuine.groupby(dimension):
            groups[str(value)] = float(group["_challenged"].mean()) if len(group) else None
        rates = [rate for rate in groups.values() if rate is not None]
        parity = min(rates) / max(rates) if rates and max(rates) > 0 else None
        result[dimension] = {"genuine_friction_rate": groups, "parity_ratio": parity, "meets_80_percent_rule": parity is None or parity >= 0.8}
    return result


def _write_reports(output: Path, metadata: dict[str, Any]) -> None:
    metrics = metadata["metrics"]
    baseline, xgb, isolation = metrics["rules_baseline"], metrics["xgboost"], metrics["isolation_forest"]
    report = f"""# FraudShield synthetic evaluation report

This report is generated from synthetic data and is not evidence of production fraud-detection performance.

| Model | PR-AUC | ROC-AUC | Precision | Recall | F1 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Rules baseline | {baseline['pr_auc']:.4f} | {baseline['roc_auc']:.4f} | {baseline['precision']:.4f} | {baseline['recall']:.4f} | {baseline['f1']:.4f} |
| XGBoost | {xgb['pr_auc']:.4f} | {xgb['roc_auc']:.4f} | {xgb['precision']:.4f} | {xgb['recall']:.4f} | {xgb['f1']:.4f} |
| Isolation Forest | {isolation['pr_auc']:.4f} | {isolation['roc_auc']:.4f} | {isolation['precision']:.4f} | {isolation['recall']:.4f} | {isolation['f1']:.4f} |

## Confusion matrices

- Rules baseline: `{baseline['confusion_matrix']}`
- XGBoost: `{xgb['confusion_matrix']}`
- Isolation Forest: `{isolation['confusion_matrix']}`

The XGBoost PR-AUC target of two times the rules baseline was {'met' if metadata['xgboost_pr_auc_target_met'] else 'not met'} on this run. Test data was never used to choose the operating threshold.
"""
    card = f"""# FraudShield model card

## Scope

This is an XGBoost fraud-risk model trained only on synthetic data. It is not production validated and must not replace the Spring Boot rules engine or human review.

## Operating threshold

The threshold is {metadata['operating_threshold']:.2f}, selected on the validation period by minimizing missed-fraud amount plus ₹150 per false alarm. Validation cost at the selected threshold: ₹{metadata['validation_cost']:.2f}.

## Fairness check

Test-period genuine-transaction friction rates and parity ratios are recorded below. A ratio below 0.80 requires investigation and calibration before production use.

```json
{json.dumps(metadata['fairness'], indent=2)}
```
"""
    (output / "evaluation_report.md").write_text(report)
    (output / "model_card.md").write_text(card)


def train_model(data_path: str | Path, artifact_dir: str | Path, model_version: str = "xgboost-v1") -> dict[str, Any]:
    try:
        from sklearn.ensemble import IsolationForest
        from xgboost import XGBClassifier
    except ImportError as exc:
        raise RuntimeError("Install requirements.txt before training: xgboost and scikit-learn are required") from exc

    data = pd.read_csv(data_path)
    required = set(MODEL_FEATURES) | {"txn_id", "txn_ts", "city_tier", "is_fraud"}
    missing = sorted(required - set(data.columns))
    if missing:
        raise ValueError(f"training data missing columns: {missing}")
    train, validation, test = _out_of_time_split(data)
    X_train, X_validation, X_test = _encode(train), _encode(validation), _encode(test)
    y_train, y_validation, y_test = (frame["is_fraud"].astype(int).to_numpy() for frame in (train, validation, test))
    negative, positive = np.bincount(y_train, minlength=2)
    model = XGBClassifier(n_estimators=220, max_depth=4, learning_rate=0.05, subsample=0.85, colsample_bytree=0.85, objective="binary:logistic", eval_metric="logloss", random_state=RANDOM_SEED, n_jobs=1, scale_pos_weight=float(negative / max(positive, 1)))
    model.fit(X_train, y_train, eval_set=[(X_validation, y_validation)], verbose=False)
    validation_probability, test_probability = model.predict_proba(X_validation)[:, 1], model.predict_proba(X_test)[:, 1]
    threshold, validation_cost = _cost_optimal_threshold(y_validation, validation_probability, validation["amount"].to_numpy(float))

    baseline_score = test.apply(compute_baseline_score, axis=1).to_numpy(float) / 100.0
    isolation = IsolationForest(random_state=RANDOM_SEED, contamination=0.015, n_estimators=200)
    isolation.fit(X_train)
    train_raw, validation_raw, test_raw = -isolation.score_samples(X_train), -isolation.score_samples(X_validation), -isolation.score_samples(X_test)
    minimum, maximum = float(train_raw.min()), float(train_raw.max())
    normalize = lambda values: np.clip((values - minimum) / max(maximum - minimum, 1e-9), 0, 1)
    isolation_validation, isolation_test = normalize(validation_raw), normalize(test_raw)
    isolation_threshold, _ = _cost_optimal_threshold(y_validation, isolation_validation, validation["amount"].to_numpy(float))

    metrics = {
        "rules_baseline": _metrics(y_test, baseline_score, 0.5),
        "xgboost": _metrics(y_test, test_probability, threshold),
        "isolation_forest": _metrics(y_test, isolation_test, isolation_threshold),
    }
    output = Path(artifact_dir)
    output.mkdir(parents=True, exist_ok=True)
    model.get_booster().save_model(str(output / "model.json"))
    metadata = {
        "model_version": model_version,
        "feature_order": list(MODEL_FEATURES),
        "encoded_feature_order": list(ENCODED_FEATURES),
        "categorical_features": list(CATEGORICAL_FEATURES),
        "numeric_features": list(NUMERIC_FEATURES),
        "weights_only_inference": True,
        "random_seed": RANDOM_SEED,
        "synthetic_data": True,
        "split": {"train_before": "2026-01-01", "test_on_or_after": "2026-01-01"},
        "operating_threshold": threshold,
        "validation_cost": validation_cost,
        "metrics": metrics,
        "fairness": _fairness(test, test_probability, threshold),
        "xgboost_pr_auc_target_met": metrics["xgboost"]["pr_auc"] >= 2 * metrics["rules_baseline"]["pr_auc"],
    }
    (output / "metadata.json").write_text(json.dumps(metadata, indent=2))
    _write_reports(output, metadata)
    return metadata


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train the FraudShield XGBoost model offline")
    parser.add_argument("--data", required=True, type=Path)
    parser.add_argument("--artifacts", default=Path(__file__).with_name("artifacts"), type=Path)
    args = parser.parse_args()
    print(json.dumps(train_model(args.data, args.artifacts), indent=2))
