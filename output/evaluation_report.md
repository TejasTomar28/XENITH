# FraudShield AI Evaluation Report

> **Data**: Synthetic dataset — 5,000 rows generated with fixed seed 42, 12% fraud prevalence.
> **Split**: Out-of-time — train on `txn_ts < 2026-01-01+05:30`, test on `txn_ts >= 2026-01-01+05:30`.
> **Train period**: 1,951 rows (231 fraud / 1,720 genuine).
> **Test period**: 3,049 rows (358 fraud / 2,691 genuine).
>
> This report is generated from **synthetic data only**. It is **not** evidence of production fraud-detection performance.
> Test data was **never used** to choose the operating threshold; all threshold selection was done on the validation period.

---

## 1. Model Performance Summary

| Model | PR-AUC | ROC-AUC | Precision | Recall | F1 | Threshold |
|---|---:|---:|---:|---:|---:|---:|
| **Rules Baseline** | 0.1226 | 0.5178 | 0.1468 | 0.0894 | 0.1111 | 0.50 |
| **XGBoost** | **0.2490** | **0.6673** | 0.1344 | 0.9022 | 0.2340 | 0.07 |
| **Isolation Forest** | 0.2079 | 0.6381 | 0.1302 | 0.9134 | 0.2280 | 0.21 |

**Primary metric is PR-AUC** (area under Precision-Recall curve), preferred for imbalanced datasets.
The AI-1 target of **XGBoost PR-AUC >= 2x Rules Baseline** is **MET**: 0.2490 >= 2 x 0.1226 = 0.2452.

---

## 2. Confusion Matrices (Test Period)

### 2a. Rules Baseline (threshold = 0.50)

|  | **Predicted Genuine** | **Predicted Fraud** |
|---|---:|---:|
| **Actual Genuine** | 2,505 (TN) | 186 (FP) |
| **Actual Fraud** | 326 (FN) | 32 (TP) |

- False Negative Rate: 91.1% (baseline misses almost all fraud)
- False Positive Rate: 6.9%

### 2b. XGBoost (threshold = 0.07, cost-optimised on validation set)

|  | **Predicted Genuine** | **Predicted Fraud** |
|---|---:|---:|
| **Actual Genuine** | 611 (TN) | 2,080 (FP) |
| **Actual Fraud** | 35 (FN) | 323 (TP) |

- **Recall 90.2%** catches 323 of 358 fraud cases
- The low threshold (0.07) reflects cost-optimisation: missed fraud costs much more than a false alarm (Rs 150)

### 2c. Isolation Forest (threshold = 0.21, cost-optimised on validation set)

|  | **Predicted Genuine** | **Predicted Fraud** |
|---|---:|---:|
| **Actual Genuine** | 507 (TN) | 2,184 (FP) |
| **Actual Fraud** | 31 (FN) | 327 (TP) |

- Unsupervised detector; no labels used during training
- ROC-AUC 0.638 vs baseline 0.518 — material lift on an unsupervised basis

---

## 3. XGBoost vs Rules Baseline — PR-AUC Target

| | PR-AUC |
|---|---:|
| Rules Baseline | 0.1226 |
| 2x Baseline target | 0.2452 |
| XGBoost achieved | **0.2490** PASS |

The supervised model exceeds the 2x baseline PR-AUC requirement specified in AI-1.

---

## 4. Isolation Forest vs Baseline — ROC-AUC (AI-4)

| | ROC-AUC |
|---|---:|
| Rules Baseline | 0.5178 |
| Isolation Forest | **0.6381** PASS |

The unsupervised Isolation Forest (AI-4) outperforms the rules-only ROC-AUC baseline by 12 points.
Both XGBoost and Isolation Forest scores are exposed to analysts as required by AI-4.

---

## 5. Feature Importance (XGBoost — SHAP aggregated to 15 base features)

| Rank | Feature | Direction |
|---:|---|---|
| 1 | `distance_from_home_km` | Higher distance -> higher fraud risk |
| 2 | `merchant_risk` | BLACKLISTED/HIGH -> strong fraud signal |
| 3 | `card_age_days` | Newer cards -> higher risk |
| 4 | `new_merchant_flag` | New merchants -> higher risk |
| 5 | `amount_to_avg30_ratio` | High ratio -> higher risk |
| 6 | `velocity_10m` | Higher velocity -> higher risk |
| 7 | `amount` | High amounts -> elevated risk |

---

## 6. Training Details

| Parameter | Value |
|---|---|
| Algorithm | XGBoost binary:logistic |
| n_estimators | 220 |
| max_depth | 4 |
| learning_rate | 0.05 |
| subsample | 0.85 |
| colsample_bytree | 0.85 |
| Random seed | 42 |
| Class imbalance | scale_pos_weight = negative/positive count (training data only) |
| Second detector | IsolationForest (n_estimators=200, contamination=0.015, seed=42) |
| Threshold selection | Cost minimisation on validation period only (never test) |

---

## 7. Prohibited Feature Check (AI-1)

| Feature | Status |
|---|---|
| `txn_id` | EXCLUDED |
| `txn_ts` | EXCLUDED (used only for train/test split boundary) |
| `city_tier` | EXCLUDED (used only for segment fairness evaluation) |

All 15 document-defined model features are present; no prohibited features appear in training.

---

## 8. Known Limitations

1. **Synthetic data only** — the fraud signal is deterministic from a logit function; real distributions will differ.
2. **High FP rate at low threshold** — the cost-optimised threshold of 0.07 produces ~77% challenge rate on genuine transactions. This is expected given the cost asymmetry (missed fraud >> Rs 150 false-alarm cost) and must be recalibrated with real labelled data before production.
3. **Baseline weakness** — the rules baseline recall is only 8.9%; the heuristic soft-rule scoring alone is a weak discriminator by design.
