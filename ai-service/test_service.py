import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from features import MODEL_FEATURES, derive_features, validate_features
from synthetic_data import generate_rows, write_csv


class FraudShieldServiceTests(unittest.TestCase):
    def setUp(self):
        self.transaction = {
            "txn_id": "T200000007", "card_id": "C1007", "txn_ts": "2026-03-04T14:05:00+05:30",
            "amount": "15000.00", "merchant_id": "M00451", "mcc": "5732", "channel": "ECOM",
            "txn_country": "IN",
        }
        self.card = {"card_type": "DEBIT", "home_country": "IN", "issue_date": "2025-01-01"}
        self.merchant = {"merchant_risk": "LOW"}
        self.city = {"lat": 19.076, "lon": 72.8777}

    def test_derives_only_document_features_and_history_features(self):
        features = derive_features(
            self.transaction, self.card, self.merchant, self.city, self.city,
            {"risk_level": "LOW"}, "RETAIL", [
                {"card_id": "C1007", "txn_ts": "2026-03-04T14:00:00+05:30", "amount": "2400.00", "merchant_id": "M00450", "status": "APPROVED"},
            ],
        )
        self.assertEqual(set(features), set(MODEL_FEATURES))
        self.assertEqual(features["velocity_10m"], 1)
        self.assertEqual(features["amount_to_avg30_ratio"], 6.25)
        self.assertTrue(features["new_merchant_flag"])
        self.assertEqual(validate_features(features), features)

    def test_invalid_schema_is_rejected(self):
        with self.assertRaises(ValueError):
            validate_features({"amount": 1})

    def test_rules_baseline_matches_document_definition(self):
        from train import compute_baseline_score

        row = {key: 0 for key in MODEL_FEATURES}
        row.update({"velocity_10m": 5, "merchant_risk": "LOW"})
        self.assertEqual(compute_baseline_score(row), 100)
        row.update({"velocity_10m": 3, "amount": 25000, "amount_to_avg30_ratio": 5.1, "is_international": True, "country_risk_high": True, "mcc": "4829", "local_hour": 2, "channel": "ECOM", "new_merchant_flag": True})
        self.assertEqual(compute_baseline_score(row), 100)

    def test_synthetic_generation_is_reproducible_and_has_both_classes(self):
        first = generate_rows(500, seed=42)
        second = generate_rows(500, seed=42)
        self.assertEqual(first, second)
        self.assertEqual(set(first[0]), {"txn_id", "txn_ts", *MODEL_FEATURES, "city_tier", "is_fraud"})
        self.assertEqual({row["is_fraud"] for row in first}, {0, 1})

    @unittest.skipUnless(importlib.util.find_spec("xgboost") and importlib.util.find_spec("sklearn") and importlib.util.find_spec("shap"), "ML dependencies are not installed")
    def test_training_and_shap_integration(self):
        from model import ModelBundle
        from train import train_model

        with tempfile.TemporaryDirectory() as directory:
            data_path = Path(directory) / "data.csv"
            artifact_dir = Path(directory) / "artifacts"
            write_csv(data_path, count=2500, seed=42)
            train_model(data_path, artifact_dir)
            result = ModelBundle.load(artifact_dir).predict({key: generate_rows(1, 7)[0][key] for key in MODEL_FEATURES})
            self.assertTrue(0 <= result["fraud_score"] <= 1)
            self.assertEqual(len(result["top_factors"]), 3)
            self.assertTrue(all(factor["feature"] in MODEL_FEATURES for factor in result["top_factors"]))

    @unittest.skipUnless(importlib.util.find_spec("fastapi"), "FastAPI is not installed")
    def test_internal_api_key_is_required(self):
        from fastapi import HTTPException
        from app import require_internal_key

        with mock.patch.dict(os.environ, {"AI_INTERNAL_API_KEY": ""}):
            with self.assertRaises(HTTPException):
                require_internal_key("anything")
        with mock.patch.dict(os.environ, {"AI_INTERNAL_API_KEY": "expected-key"}):
            for supplied in (None, "wrong-key"):
                with self.assertRaises(HTTPException) as raised:
                    require_internal_key(supplied)
                self.assertEqual(raised.exception.status_code, 401)
            self.assertIsNone(require_internal_key("expected-key"))


if __name__ == "__main__":
    unittest.main()
