"""Derive the document-defined AI features from a transaction and history."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from math import asin, cos, radians, sin, sqrt
from typing import Any, Iterable


IST = timezone(timedelta(hours=5, minutes=30))
MODEL_FEATURES = (
    "card_type",
    "channel",
    "mcc",
    "mcc_category",
    "merchant_risk",
    "amount",
    "local_hour",
    "is_international",
    "country_risk_high",
    "distance_from_home_km",
    "velocity_10m",
    "velocity_24h",
    "amount_to_avg30_ratio",
    "new_merchant_flag",
    "card_age_days",
)
CATEGORICAL_FEATURES = ("card_type", "channel", "mcc", "mcc_category", "merchant_risk")
NUMERIC_FEATURES = tuple(name for name in MODEL_FEATURES if name not in CATEGORICAL_FEATURES)
ALLOWED_CARD_TYPES = {"DEBIT", "CREDIT"}
ALLOWED_CHANNELS = {"POS", "ECOM", "ATM", "CONTACTLESS"}
ALLOWED_MERCHANT_RISK = {"LOW", "MEDIUM", "HIGH", "BLACKLISTED"}
ENCODED_CATEGORIES = {
    "card_type": ("DEBIT", "CREDIT"),
    "channel": ("POS", "ECOM", "ATM", "CONTACTLESS"),
    "mcc": ("4829", "5967", "6051", "7995", "5411", "5732", "5812", "OTHER"),
    "mcc_category": ("RETAIL", "TRAVEL", "SERVICES", "ENTERTAINMENT", "OTHER"),
    "merchant_risk": ("LOW", "MEDIUM", "HIGH", "BLACKLISTED"),
}
ENCODED_FEATURES = tuple(
    [f"{name}={value}" for name, values in ENCODED_CATEGORIES.items() for value in values]
    + list(NUMERIC_FEATURES)
)


def _timestamp(value: str | datetime) -> datetime:
    parsed = value if isinstance(value, datetime) else datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        raise ValueError("txn_ts must include a timezone offset")
    return parsed.astimezone(IST)


def haversine_km(first: dict[str, Any], second: dict[str, Any]) -> float:
    """Return the great-circle distance using the document's 6371 km radius."""
    lat1, lon1 = radians(float(first["lat"])), radians(float(first["lon"]))
    lat2, lon2 = radians(float(second["lat"])), radians(float(second["lon"]))
    d_lat, d_lon = lat2 - lat1, lon2 - lon1
    a = sin(d_lat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(d_lon / 2) ** 2
    return 2 * 6371 * asin(sqrt(a))


def _prior(history: Iterable[dict[str, Any]], card_id: str, current: datetime):
    for row in history:
        if row.get("card_id") != card_id:
            continue
        row_ts = _timestamp(row["txn_ts"])
        if row_ts < current:
            yield row, row_ts


def derive_features(
    transaction: dict[str, Any],
    card: dict[str, Any],
    merchant: dict[str, Any],
    transaction_city: dict[str, Any],
    home_city: dict[str, Any],
    country_risk: dict[str, Any],
    mcc_category: str,
    history: Iterable[dict[str, Any]],
) -> dict[str, Any]:
    """Convert transaction details plus prior transactions into model features.

    IDs and reference records are used only for lookup/derivation; they are not
    returned as predictive features.
    """
    current = _timestamp(transaction["txn_ts"])
    amount = float(transaction["amount"])
    prior = list(_prior(history, transaction["card_id"], current))

    def within(row_ts: datetime, window: timedelta) -> bool:
        delta = current - row_ts
        return timedelta(0) < delta <= window

    velocity_10m = sum(within(ts, timedelta(minutes=10)) for _, ts in prior)
    velocity_24h = sum(within(ts, timedelta(hours=24)) for _, ts in prior)
    approved_30d = [
        row
        for row, ts in prior
        if row.get("status") == "APPROVED" and within(ts, timedelta(days=30))
    ]
    avg = sum(float(row["amount"]) for row in approved_30d) / len(approved_30d) if approved_30d else None
    merchant_seen = any(
        row.get("merchant_id") == transaction["merchant_id"]
        and within(ts, timedelta(days=90))
        for row, ts in prior
    )
    issue_date = datetime.fromisoformat(str(card["issue_date"])).date()

    return {
        "card_type": card["card_type"],
        "channel": transaction["channel"],
        "mcc": str(transaction["mcc"]),
        "mcc_category": mcc_category,
        "merchant_risk": merchant["merchant_risk"],
        "amount": amount,
        "local_hour": current.hour,
        "is_international": transaction["txn_country"] != card["home_country"],
        "country_risk_high": country_risk["risk_level"] == "HIGH",
        "distance_from_home_km": haversine_km(home_city, transaction_city),
        "velocity_10m": velocity_10m,
        "velocity_24h": velocity_24h,
        "amount_to_avg30_ratio": amount / avg if avg else None,
        "new_merchant_flag": not merchant_seen,
        "card_age_days": (current.date() - issue_date).days,
    }


def validate_features(features: dict[str, Any]) -> dict[str, Any]:
    """Validate and normalize exactly the model input schema."""
    if set(features) != set(MODEL_FEATURES):
        missing = sorted(set(MODEL_FEATURES) - set(features))
        extra = sorted(set(features) - set(MODEL_FEATURES))
        raise ValueError(f"feature schema mismatch; missing={missing}, extra={extra}")
    normalized = dict(features)
    normalized["mcc"] = str(normalized["mcc"])
    for key in CATEGORICAL_FEATURES:
        if not isinstance(normalized[key], str) or not normalized[key]:
            raise ValueError(f"{key} must be a non-empty string")
    if normalized["card_type"] not in ALLOWED_CARD_TYPES:
        raise ValueError("card_type must be DEBIT or CREDIT")
    if normalized["channel"] not in ALLOWED_CHANNELS:
        raise ValueError("channel must be POS, ECOM, ATM or CONTACTLESS")
    if normalized["merchant_risk"] not in ALLOWED_MERCHANT_RISK:
        raise ValueError("merchant_risk is not supported")
    if len(str(normalized["mcc"])) != 4 or not str(normalized["mcc"]).isdigit():
        raise ValueError("mcc must be a four-digit string")
    for key in ("amount", "distance_from_home_km", "velocity_10m", "velocity_24h", "local_hour", "card_age_days"):
        if isinstance(normalized[key], bool) or not isinstance(normalized[key], (int, float)):
            raise ValueError(f"{key} must be numeric")
    if normalized["amount"] <= 0 or normalized["distance_from_home_km"] < 0 or normalized["velocity_10m"] < 0 or normalized["velocity_24h"] < 0 or normalized["card_age_days"] < 0:
        raise ValueError("numeric feature is out of range")
    if not 0 <= normalized["local_hour"] <= 23:
        raise ValueError("local_hour must be between 0 and 23")
    for key in ("is_international", "country_risk_high", "new_merchant_flag"):
        if not isinstance(normalized[key], bool):
            raise ValueError(f"{key} must be boolean")
    ratio = normalized["amount_to_avg30_ratio"]
    if ratio is not None and (isinstance(ratio, bool) or not isinstance(ratio, (int, float)) or ratio < 0):
        raise ValueError("amount_to_avg30_ratio must be null or a nonnegative number")
    return normalized


def encode_features(features: dict[str, Any]) -> list[float]:
    """Map validated raw attributes into the fixed training/inference matrix."""
    normalized = validate_features(features)
    encoded: list[float] = []
    for name, values in ENCODED_CATEGORIES.items():
        value = str(normalized[name])
        if name in {"mcc", "mcc_category"} and value not in values:
            value = "OTHER"
        encoded.extend([1.0 if value == category else 0.0 for category in values])
    encoded.extend(float("nan") if normalized[name] is None else float(normalized[name]) for name in NUMERIC_FEATURES)
    return encoded
