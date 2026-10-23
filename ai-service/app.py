"""FastAPI adapter for the shared internal AI prediction contract."""

from __future__ import annotations

import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

try:
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel, ConfigDict, Field, field_validator
except ImportError as exc:  # pragma: no cover - dependency-gated runtime
    raise RuntimeError("Install ai-service/requirements.txt to run the HTTP service") from exc

try:
    from .features import validate_features
    from .model import ModelArtifactError, get_model_bundle
except ImportError:  # direct execution by uvicorn from the ai-service directory
    from features import validate_features
    from model import ModelArtifactError, get_model_bundle


class FeatureRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    features: dict[str, Any]

    @field_validator("features")
    @classmethod
    def validate_model_features(cls, value: dict[str, Any]) -> dict[str, Any]:
        return validate_features(value)


class TopFactor(BaseModel):
    feature: str
    explanation: str
    contribution: float


class PredictionResponse(BaseModel):
    fraud_score: float = Field(ge=0, le=1)
    top_factors: list[TopFactor] = Field(min_length=3, max_length=3)
    model_version: str


def _artifact_dir() -> str:
    return os.getenv("FRAUDSHIELD_MODEL_DIR", str(Path(__file__).with_name("artifacts")))


@asynccontextmanager
async def lifespan(_: FastAPI):
    try:
        get_model_bundle(_artifact_dir())
    except ModelArtifactError:
        pass
    yield


app = FastAPI(title="FraudShield AI Service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str | None]:
    try:
        bundle = get_model_bundle(_artifact_dir())
        return {"status": "ready", "model_version": bundle.model_version if hasattr(bundle, "model_version") else "xgboost-v1"}
    except ModelArtifactError:
        return {"status": "not_ready", "model_version": None}


@app.post("/internal/v1/predictions", response_model=PredictionResponse)
def predict(request: FeatureRequest) -> PredictionResponse:
    try:
        return PredictionResponse(**get_model_bundle(_artifact_dir()).predict(request.features))
    except ModelArtifactError as exc:
        raise HTTPException(status_code=503, detail="AI model is unavailable") from exc
