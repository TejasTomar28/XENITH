# FraudShield model card

## Scope

This is an XGBoost fraud-risk model trained only on synthetic data. It is not production validated and must not replace the Spring Boot rules engine or human review.

## Operating threshold

The threshold is 0.07, selected on the validation period by minimizing missed-fraud amount plus ₹150 per false alarm. Validation cost at the selected threshold: ₹38516.28.

## Fairness check

Test-period genuine-transaction friction rates and parity ratios are recorded below. A ratio below 0.80 requires investigation and calibration before production use.

```json
{
  "city_tier": {
    "genuine_friction_rate": {
      "INTL": 0.7710011507479861,
      "METRO": 0.7703141928494042,
      "NON_METRO": 0.7775305895439377
    },
    "parity_ratio": 0.990718825996587,
    "meets_80_percent_rule": true
  },
  "card_type": {
    "genuine_friction_rate": {
      "CREDIT": 0.7798657718120805,
      "DEBIT": 0.7702980472764646
    },
    "parity_ratio": 0.9877315752512326,
    "meets_80_percent_rule": true
  }
}
```
