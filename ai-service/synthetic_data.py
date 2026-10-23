"""Reproducible synthetic labelled data using only the DOCX model attributes."""

from __future__ import annotations

import argparse
import csv
import math
import random
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

try:
    from .features import MODEL_FEATURES
except ImportError:  # direct execution from the ai-service directory
    from features import MODEL_FEATURES


IST = timezone(timedelta(hours=5, minutes=30))
OUTPUT_FIELDS = ("txn_id", "txn_ts", *MODEL_FEATURES, "city_tier", "is_fraud")


def _sigmoid(value: float) -> float:
    return 1 / (1 + math.exp(-value))


def generate_rows(count: int = 1000, seed: int = 42, fraud_rate: float = 0.12) -> list[dict[str, Any]]:
    """Generate mixed legitimate/fraudulent examples with configurable prevalence."""
    if count < 1 or not 0 < fraud_rate < 1:
        raise ValueError("count must be positive and fraud_rate must be between 0 and 1")
    rng = random.Random(seed)
    rows: list[dict[str, Any]] = []
    intercept = math.log(fraud_rate / (1 - fraud_rate)) - 1.4
    for index in range(1, count + 1):
        ratio = None if rng.random() < 0.15 else round(rng.lognormvariate(0.05, 0.75), 4)
        features: dict[str, Any] = {
            "card_type": rng.choices(["DEBIT", "CREDIT"], [72, 28])[0],
            "channel": rng.choices(["POS", "ECOM", "ATM", "CONTACTLESS"], [42, 30, 12, 16])[0],
            "mcc": rng.choices(["4829", "5732", "5967", "6051", "7995", "5411", "5812"], [4, 20, 5, 4, 4, 42, 21])[0],
            "mcc_category": rng.choices(["RETAIL", "TRAVEL", "SERVICES", "ENTERTAINMENT"], [48, 18, 24, 10])[0],
            "merchant_risk": rng.choices(["LOW", "MEDIUM", "HIGH", "BLACKLISTED"], [74, 18, 7, 1])[0],
            "amount": round(rng.lognormvariate(7.2, 1.0), 2),
            "local_hour": rng.randrange(24),
            "is_international": rng.random() < 0.10,
            "country_risk_high": rng.random() < 0.07,
            "distance_from_home_km": round(max(0.0, rng.gauss(120, 260)), 2),
            "velocity_10m": rng.choices(range(8), [54, 17, 10, 7, 5, 3, 2, 2])[0],
            "velocity_24h": rng.choices(range(25), [28, 14, 10, 8, 7, 6, 5, 4, 3, 3, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1])[0],
            "amount_to_avg30_ratio": ratio,
            "new_merchant_flag": rng.random() < 0.18,
            "card_age_days": rng.randrange(30, 2500),
        }
        ratio_signal = min(ratio / 5, 1.0) if ratio is not None else min(features["amount"] / 50000, 1.0)
        merchant_signal = {"LOW": 0.0, "MEDIUM": 0.9, "HIGH": 2.2, "BLACKLISTED": 3.0}[features["merchant_risk"]]
        distance_signal = min(features["distance_from_home_km"] / 750, 1.0)
        new_card_signal = features["card_age_days"] < 180
        suspicious = (
            intercept
            + 0.15 * min(features["velocity_10m"] / 5, 1.0)
            + 0.08 * min(features["velocity_24h"] / 10, 1.0)
            + 0.12 * ratio_signal
            + 0.55 * features["new_merchant_flag"]
            + 0.25 * features["country_risk_high"]
            + 0.20 * features["is_international"]
            + 1.5 * distance_signal
            + merchant_signal
            + 1.15 * new_card_signal
            + 0.75 * (features["channel"] == "ECOM" and features["new_merchant_flag"])
            + 0.85 * (features["merchant_risk"] in {"HIGH", "BLACKLISTED"} and features["distance_from_home_km"] > 500)
            + 0.55 * (features["local_hour"] < 5 and features["channel"] == "ECOM")
            + rng.gauss(0, 0.28)
        )
        row = {"txn_id": f"T{index:09d}", "txn_ts": datetime(2025, 9, 1, tzinfo=IST) + timedelta(minutes=index * 90), **features}
        row.update({"city_tier": rng.choice(["METRO", "NON_METRO", "INTL"]), "is_fraud": int(rng.random() < _sigmoid(suspicious))})
        rows.append(row)
    return rows


def write_csv(path: str | Path, count: int = 1000, seed: int = 42, fraud_rate: float = 0.12) -> Path:
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()
        for row in generate_rows(count, seed, fraud_rate):
            writer.writerow({key: row[key].isoformat() if isinstance(row[key], datetime) else row[key] for key in OUTPUT_FIELDS})
    return destination


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate synthetic FraudShield labelled transactions")
    parser.add_argument("output", type=Path)
    parser.add_argument("--count", type=int, default=1000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--fraud-rate", type=float, default=0.12)
    args = parser.parse_args()
    write_csv(args.output, args.count, args.seed, args.fraud_rate)
