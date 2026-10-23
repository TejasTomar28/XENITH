# FraudShield backend integration handoff

All JSON uses snake_case. User routes require `Authorization: Bearer <JWT>`. The gateway route accepts only `X-API-Key`. Errors use `{timestamp, code, message, path, field_errors, request_id}`; common statuses are 401, 403, 404, 409 and 422.

The local backend base URL is `http://localhost:8080`; set the frontend's public API base URL to that value. `FRONTEND_ORIGINS` is a comma-separated allowlist and defaults to `http://localhost:5173`. CORS allows `Authorization` and `Content-Type` on `/api/**` and exposes `Content-Disposition`. It deliberately excludes `X-API-Key` because gateway credentials must not be shipped in Vite code.

## Authentication and gateway

- `POST /api/v1/auth/login` — `{ "username": "analyst", "password": "..." }`; returns `{ "access_token": "...", "token_type": "Bearer", "expires_in": 3600, "user": {...} }`.
- `GET /api/v1/auth/me` — returns the authenticated user and role.
- `POST /api/v1/authorisations` — gateway key; accepts `txn_id, card_id, txn_ts, amount, currency, merchant_id, mcc, channel, txn_city, txn_country`. Returns `decision, reason_codes, rule_risk_score, status, alert_id, processed_at, fraud_score, top_factors, model_status`.

Example request and response shapes:

```json
{"txn_id":"T200000007","card_id":"CARD1","txn_ts":"2026-03-04T14:05:00+05:30","amount":"15000.00","currency":"INR","merchant_id":"MER1","mcc":"5411","channel":"ECOM","txn_city":"Mumbai","txn_country":"IN"}
```

```json
{"txn_id":"T200000007","decision":"APPROVE","reason_codes":[],"rule_risk_score":0,"status":"APPROVED","alert_id":null,"processed_at":"2026-03-04T14:05:01+05:30","fraud_score":null,"top_factors":[],"model_status":"MODEL_UNAVAILABLE"}
```

An identical retry returns the immutable original response even after a later step-up transition. Reusing the ID with changed fields returns 409. Invalid fields return 422, unknown references return 404, and missing/invalid gateway authentication returns 401.

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

```json
{"features":{"card_type":"DEBIT","channel":"ECOM","mcc":"5411","mcc_category":"RETAIL","merchant_risk":"LOW","amount":15000.00,"local_hour":14,"is_international":false,"country_risk_high":false,"distance_from_home_km":0.0,"velocity_10m":0,"velocity_24h":0,"amount_to_avg30_ratio":null,"new_merchant_flag":true,"card_age_days":427}}
```

```json
{"fraud_score":0.82,"model_version":"xgb-v1","top_factors":[{"feature":"amount","explanation":"Amount contributed to risk"},{"feature":"channel","explanation":"Channel contributed to risk"},{"feature":"new_merchant_flag","explanation":"Merchant novelty contributed to risk"}]}
```

## Environment and startup

Required runtime variables are `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `GATEWAY_API_KEY` and `AI_INTERNAL_API_KEY`. Optional integration variables are `JWT_ISSUER`, `JWT_EXPIRATION_SECONDS`, `AI_SERVICE_URL`, `AI_TIMEOUT_MS` and `FRONTEND_ORIGINS`. Start PostgreSQL, seed the official pack using `README.md`, then run `./mvnw spring-boot:run`. Flyway applies migrations automatically.

The official data pack is absent from the repository, so an integration environment must supply and seed it before business APIs can resolve cards, merchants, geography and active rule configuration.

## Batch command

Under the `evaluation` profile, `fraudshield.dataset.command=batch` accepts `fraudshield.dataset.input` and optional `fraudshield.dataset.output-directory` (default `target/fraudshield-output`). It resets prior resettable runs, sorts by `txn_ts` then `txn_id`, isolates history from LIVE/DEMO traffic and writes the three official CSV schemas.

## Contract decisions still awaiting shared agreement

- The manager configuration PUT currently accepts `settings` as a JSON object; `docs/api-contracts.md` proposes an array of key/value objects pending the official JSON schema.
- Manager `REJECT` maps to `CONFIRMED_FRAUD` and blocks the card, inferred from the only documented terminal lifecycle.
- Dashboard attribution uses transaction timestamp and original decision, with a default trailing 30-day range.
- Transaction audit lookup and export aggregate linked transaction, step-up, alert, proposal and same-transition card events while preserving each event's real entity identity. Alert/card/configuration lookups remain entity-specific.
- A frontend simulator cannot directly call the gateway-only route. It needs an approved server-side adapter or demonstration contract; do not put the gateway key in Vite variables.
