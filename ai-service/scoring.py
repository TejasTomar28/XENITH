"""Compatibility entry point backed by the trained XGBoost model."""

from __future__ import annotations

from typing import Any

try:
    from .model import predict_features
except ImportError:  # direct execution from the ai-service directory
    from model import predict_features


def score_features(features: dict[str, Any]) -> dict[str, Any]:
    """Predict with persisted model artifacts; never fall back to a heuristic."""
    return predict_features(features)
