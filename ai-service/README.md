# FraudShield AI Service

This FastAPI service owns model feature validation, XGBoost inference, and SHAP explanations. Spring Boot remains responsible for transaction history, rules R1–R13, final decisions, persistence, and model-failure fallback.

The model uses only the attributes defined in `case_01_FraudShield_V1.0.docx`:

`card_type`, `channel`, `mcc`, `mcc_category`, `merchant_risk`, `amount`, `local_hour`, `is_international`, `country_risk_high`, `distance_from_home_km`, `velocity_10m`, `velocity_24h`, `amount_to_avg30_ratio`, `new_merchant_flag`, and `card_age_days`.

## Run the pipeline

```bash
cd ai-service
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python synthetic_data.py synthetic/labelled.csv --count 10000 --seed 42 --fraud-rate 0.12
python train.py --data synthetic/labelled.csv --artifacts artifacts
uvicorn app:app --host 0.0.0.0 --port 8000
```

Training uses the mandatory out-of-time boundary: train/validation data is before `2026-01-01T00:00:00+05:30`; test data is on or after it. It calculates the required rules-only baseline, trains XGBoost and an evaluation-only Isolation Forest, selects the XGBoost threshold from validation expected cost (missed-fraud amount plus ₹150 per false alarm), and writes `evaluation_report.md` and `model_card.md` under `artifacts/`.

For a production-only image, install `requirements-inference.txt` and copy only the inference source, `model.json`, and the declared runtime dependencies. The training script and synthetic data are offline tooling.

## Prediction contract

`POST /internal/v1/predictions` accepts `{ "features": { ... } }` with exactly the model fields above and returns a probability in `[0, 1]`, the model version, and exactly three factors. Factors are aggregated from the individual prediction's SHAP contributions. Contributions are reported in the model's log-odds output space; they are not causal explanations or probability deltas.

`GET /health` returns `ready` only when all model artifacts load successfully. Missing or corrupt artifacts produce `not_ready` and prediction requests return HTTP 503. There is no heuristic fallback.

Batch outputs with final decisions must be produced by Spring Boot after it applies rules and its AI escalation policy. The AI service deliberately does not make final decisions.

The generated dataset is synthetic and its metrics are useful only for validating the pipeline. They are not evidence of production fraud-detection performance. A real labelled dataset, out-of-time evaluation, calibration review, and governance approval are still required.
