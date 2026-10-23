# FraudShield synthetic evaluation report

This report is generated from synthetic data and is not evidence of production fraud-detection performance.

| Model | PR-AUC | ROC-AUC | Precision | Recall | F1 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Rules baseline | 0.1226 | 0.5178 | 0.1468 | 0.0894 | 0.1111 |
| XGBoost | 0.2490 | 0.6673 | 0.1344 | 0.9022 | 0.2340 |
| Isolation Forest | 0.2079 | 0.6381 | 0.1302 | 0.9134 | 0.2280 |

## Confusion matrices

- Rules baseline: `[[2505, 186], [326, 32]]`
- XGBoost: `[[611, 2080], [35, 323]]`
- Isolation Forest: `[[507, 2184], [31, 327]]`

The XGBoost PR-AUC target of two times the rules baseline was met on this run. Test data was never used to choose the operating threshold.
