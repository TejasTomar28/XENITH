"""Batch evaluation script: generate output/fraud_scores_output.csv from the test period.

Usage (from repo root):
    python -m ai-service.evaluate_batch --data ai-service/synthetic/labelled.csv \
        --artifacts ai-service/artifacts --output output

Or from ai-service directory:
    python evaluate_batch.py --data synthetic/labelled.csv --artifacts artifacts --output ../output
"""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

try:
    from .features import ENCODED_FEATURES, MODEL_FEATURES, encode_features
    from .train import SPLIT_DATE, _encode, _metrics, _fairness, _cost_optimal_threshold, compute_baseline_score
except ImportError:
    from features import ENCODED_FEATURES, MODEL_FEATURES, encode_features
    from train import SPLIT_DATE, _encode, _metrics, _fairness, _cost_optimal_threshold, compute_baseline_score


DECISION_THRESHOLDS = {"APPROVE": 0.70, "STEP_UP": 0.90}


def _rules_decision(fraud_score: float, operating_threshold: float) -> str:
    """Map fraud_score to a final_decision label using the document's decision escalation.

    This function represents the rules-only perspective encoded for the CSV:
    scores below the operating threshold -> APPROVE, at/above -> STEP_UP.
    The Spring Boot backend applies the full AI-2 escalation at runtime.
    """
    if fraud_score >= operating_threshold and fraud_score < DECISION_THRESHOLDS["STEP_UP"]:
        return "STEP_UP"
    elif fraud_score >= DECISION_THRESHOLDS["STEP_UP"]:
        return "BLOCK"
    return "APPROVE"


def _top_factors_csv(model: Any, matrix: np.ndarray) -> str:
    """Return 3 pipe-separated feature names using SHAP, aggregated to the 15 base features."""
    import shap

    explainer = shap.TreeExplainer(model)
    shap_values = explainer.shap_values(matrix)
    values = np.asarray(shap_values)[0]
    grouped: dict[str, float] = {feature: 0.0 for feature in MODEL_FEATURES}
    for name, value in zip(ENCODED_FEATURES, values):
        root = name.split("=", 1)[0] if "=" in name else name
        grouped[root] += float(value)
    top = sorted(grouped, key=lambda f: abs(grouped[f]), reverse=True)[:3]
    return "|".join(top)


def run_batch(data_path: str | Path, artifact_dir: str | Path, output_dir: str | Path) -> Path:
    import xgboost as xgb

    data_path = Path(data_path)
    artifact_dir = Path(artifact_dir)
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Load model and metadata
    model_path = artifact_dir / "model.json"
    if not model_path.is_file():
        raise FileNotFoundError(f"Trained model not found at {model_path}. Run train.py first.")

    booster = xgb.Booster()
    booster.load_model(str(model_path))

    metadata_path = artifact_dir / "metadata.json"
    metadata: dict[str, Any] = json.loads(metadata_path.read_text()) if metadata_path.is_file() else {}
    operating_threshold = float(metadata.get("operating_threshold", 0.5))

    # Load data and apply out-of-time split — test period only
    data = pd.read_csv(data_path)
    required = set(MODEL_FEATURES) | {"txn_id", "txn_ts", "city_tier", "is_fraud"}
    missing = sorted(required - set(data.columns))
    if missing:
        raise ValueError(f"Data missing columns: {missing}")

    data = data.assign(parsed_ts=pd.to_datetime(data["txn_ts"], utc=True))
    test = data[data["parsed_ts"] >= SPLIT_DATE].sort_values("parsed_ts").reset_index(drop=True)

    if test.empty:
        raise ValueError("No test-period rows found (txn_ts >= 2026-01-01+05:30).")

    print(f"Test period: {len(test)} rows ({test['is_fraud'].sum()} fraud, "
          f"{(test['is_fraud'] == 0).sum()} genuine)")

    X_test = _encode(test)
    dmatrix = xgb.DMatrix(X_test, feature_names=list(ENCODED_FEATURES))
    probabilities = booster.predict(dmatrix)

    # Write fraud_scores_output.csv
    output_csv = output_dir / "fraud_scores_output.csv"
    rows_written = 0
    with output_csv.open("w", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["txn_id", "fraud_score", "final_decision", "top_factors"])
        for i, (_, row) in enumerate(test.iterrows()):
            score = float(probabilities[i])
            score_rounded = round(score, 4)
            decision = _rules_decision(score, operating_threshold)
            # Compute SHAP top factors per row
            matrix_row = X_test[i : i + 1]
            factors = _top_factors_csv(booster, matrix_row)
            writer.writerow([row["txn_id"], f"{score_rounded:.4f}", decision, factors])
            rows_written += 1

    print(f"Written {rows_written} rows → {output_csv}")
    return output_csv


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate fraud_scores_output.csv from the test period")
    parser.add_argument("--data", required=True, type=Path, help="Path to labelled CSV")
    parser.add_argument("--artifacts", default=Path(__file__).with_name("artifacts"), type=Path)
    parser.add_argument("--output", default=Path(__file__).parent.parent / "output", type=Path)
    args = parser.parse_args()
    run_batch(args.data, args.artifacts, args.output)


if __name__ == "__main__":
    main()
