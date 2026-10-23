# FraudShield backend integration handoff

All JSON uses snake_case. User routes require `Authorization: Bearer <JWT>`. The gateway route accepts only `X-API-Key`. Errors use `{timestamp, code, message, path, field_errors, request_id}`; common statuses are 401, 403, 404, 409 and 422.

## Authentication and gateway

- `POST /api/v1/auth/login` — `{ "username": "analyst", "password": "..." }`; returns `{ "access_token": "...", "token_type": "Bearer", "expires_in": 3600, "user": {...} }`.
- `GET /api/v1/auth/me` — returns the authenticated user and role.
- `POST /api/v1/authorisations` — gateway key; accepts `txn_id, card_id, txn_ts, amount, currency, merchant_id, mcc, channel, txn_city, txn_country`. Returns `decision, reason_codes, rule_risk_score, status, alert_id, processed_at, fraud_score, top_factors, model_status`.

An identical transaction retry returns its stored result. Reusing the ID with changed fields returns 409.

## Cardholder

- `GET /api/v1/me/cards`
- `GET /api/v1/me/transactions?page=0&size=20`
- `GET /api/v1/me/transactions/{txn_id}`
- `GET /api/v1/me/step-ups`
- `POST /api/v1/me/step-ups/{step_up_id}/responses` with `{ "response": "CONFIRM", "otp": "123456" }` or `{ "response": "NOT_ME" }`
- `POST /api/v1/me/cards/{card_id}/block`

Cards are masked and all lookups derive ownership from the JWT. Foreign resources return 403.

## Fraud Analyst

- `GET /api/v1/analyst/alerts?page=0&size=20&status=OPEN&priority=HIGH`
- `GET /api/v1/analyst/alerts/{alert_id}`
- `GET /api/v1/analyst/alerts/{alert_id}/history`
- `POST /api/v1/analyst/alerts/{alert_id}/claim`
- `POST /api/v1/analyst/alerts/{alert_id}/resolutions` with `{ "resolution": "CONFIRMED_FRAUD|FALSE_POSITIVE", "comment": "at least 10 characters" }`

## Fraud Risk Manager

- `GET /api/v1/manager/dashboard?from=<offset-date-time>&to=<offset-date-time>`
- `GET /api/v1/manager/false-positive-proposals`
- `GET /api/v1/manager/false-positive-proposals/{proposal_id}`
- `POST /api/v1/manager/false-positive-proposals/{proposal_id}/decisions` with `{ "decision": "APPROVE|REJECT", "comment": "..." }`
- `GET /api/v1/manager/rule-configuration`
- `PUT /api/v1/manager/rule-configuration` with `{ "expected_version": "sha256", "settings": {...}, "comment": "..." }`
- `GET /api/v1/manager/audit-trails/{entity_type}/{entity_id}`
- `GET /api/v1/manager/audit-trails/{entity_type}/{entity_id}/export` (CSV for transaction, alert or card)

## Internal FastAPI call

`POST /internal/v1/predictions` is called at `AI_SERVICE_URL` with `X-Internal-API-Key`. Its `features` object contains card/channel/MCC risk features, amount, local hour, geographic and velocity values, ratio, merchant novelty and card age. It must return a probability, exactly three factors and a model version. The backend owns the 150 ms total deadline and falls back to rules when unavailable.

## Batch command

Under the `evaluation` profile, `fraudshield.dataset.command=batch` accepts `fraudshield.dataset.input` and optional `fraudshield.dataset.output-directory` (default `target/fraudshield-output`). It resets prior resettable runs, sorts by `txn_ts` then `txn_id`, isolates history from LIVE/DEMO traffic and writes the three official CSV schemas.

## Contract decisions still awaiting shared agreement

- The manager configuration PUT currently accepts `settings` as a JSON object; `docs/api-contracts.md` proposes an array of key/value objects pending the official JSON schema.
- Manager `REJECT` maps to `CONFIRMED_FRAUD` and blocks the card, inferred from the only documented terminal lifecycle.
- Dashboard attribution uses transaction timestamp and original decision, with a default trailing 30-day range.
- Audit lookup currently returns events for the requested entity identity; the proposed cross-entity aggregation/export format in D7 is not implemented.
