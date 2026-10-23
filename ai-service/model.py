"""Weights-only XGBoost inference and SHAP explanations."""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path
from typing import Any

import numpy as np

try:
    from .features import ENCODED_FEATURES, MODEL_FEATURES, encode_features, validate_features
except ImportError:  # direct execution from the ai-service directory
    from features import ENCODED_FEATURES, MODEL_FEATURES, encode_features, validate_features


MODEL_VERSION = "xgboost-v1"


class ModelArtifactError(RuntimeError):
    pass


class ModelBundle:
    def __init__(self, model: Any, explainer: Any):
        self.model = model
        self.explainer = explainer
        self.model_version = MODEL_VERSION

    @classmethod
    def load(cls, artifact_dir: str | Path) -> "ModelBundle":
        path = Path(artifact_dir) / "model.json"
        if not path.is_file():
            raise ModelArtifactError(f"model artifacts missing: {path}")
        try:
            import shap
            import xgboost as xgb

            booster = xgb.Booster()
            booster.load_model(str(path))
            return cls(booster, shap.TreeExplainer(booster))
        except Exception as exc:
            raise ModelArtifactError(f"could not load model weights: {exc}") from exc

    def predict(self, features: dict[str, Any]) -> dict[str, Any]:
        import xgboost as xgb

        normalized = validate_features(features)
        matrix = np.asarray([encode_features(normalized)], dtype=np.float32)
        prediction = float(self.model.predict(xgb.DMatrix(matrix, feature_names=list(ENCODED_FEATURES)))[0])
        if not 0 <= prediction <= 1:
            raise ModelArtifactError("model returned a probability outside [0, 1]")
        shap_values = self.explainer.shap_values(matrix)
        values = np.asarray(shap_values)[0]
        grouped = {feature: 0.0 for feature in MODEL_FEATURES}
        for name, value in zip(ENCODED_FEATURES, values):
            root = name.split("=", 1)[0] if "=" in name else name
            grouped[root] += float(value)
        top_features = sorted(grouped, key=lambda feature: abs(grouped[feature]), reverse=True)[:3]
        return {
            "fraud_score": round(prediction, 6),
            "top_factors": [
                {
                    "feature": feature,
                    "explanation": _explanation(feature, normalized[feature], grouped[feature]),
                    "contribution": round(grouped[feature], 6),
                }
                for feature in top_features
            ],
            "model_version": MODEL_VERSION,
        }


def _explanation(feature: str, value: Any, contribution: float) -> str:
    direction = "increased" if contribution >= 0 else "reduced"
    if feature == "amount_to_avg30_ratio":
        description = "No approved 30-day spending average is available" if value is None else f"Amount is {float(value):.1f}× the card's 30-day average"
    elif feature == "distance_from_home_km":
        description = f"Transaction is {float(value):.0f} km from the cardholder's home city"
    elif feature == "velocity_10m":
        description = f"{int(value)} prior transactions on this card in the last 10 minutes"
    elif feature == "velocity_24h":
        description = f"{int(value)} prior transactions on this card in the last 24 hours"
    elif feature == "local_hour":
        description = f"Transaction occurred at {int(value):02d}:00 IST"
    elif feature == "amount":
        description = f"Transaction amount is ₹{float(value):,.2f}"
    elif feature == "new_merchant_flag":
        description = "Merchant is new for this card" if value else "Merchant is already known for this card"
    elif feature == "is_international":
        description = "Transaction is international" if value else "Transaction is domestic"
    elif feature == "country_risk_high":
        description = "Transaction country has HIGH configured risk" if value else "Transaction country is not HIGH risk"
    elif feature == "merchant_risk":
        description = f"Merchant is classified as {str(value).lower()} risk"
    elif feature == "card_age_days":
        description = f"Card age is {int(value)} days"
    elif feature == "mcc":
        description = f"Merchant category code is {value}"
    elif feature == "mcc_category":
        description = f"Merchant category is {value}"
    elif feature == "channel":
        description = f"Payment channel is {value}"
    else:
        description = f"Card type is {value}"
    return f"{description}; this {direction} model risk (SHAP log-odds contribution {abs(contribution):.4f})"


@lru_cache(maxsize=1)
def get_model_bundle(artifact_dir: str | Path | None = None) -> ModelBundle:
    location = artifact_dir or os.getenv("FRAUDSHIELD_MODEL_DIR", str(Path(__file__).with_name("artifacts")))
    return ModelBundle.load(location)


def predict_features(features: dict[str, Any]) -> dict[str, Any]:
    return get_model_bundle().predict(features)
