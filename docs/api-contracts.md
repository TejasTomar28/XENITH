# FraudShield API contracts

## Status source and authority

This is the shared design contract for React → Spring Boot → FastAPI integration. It documents planned interfaces, not implemented endpoints. Business requirements come from `case_01_FraudShield_V1.0.docx` version 1.0, sections 3–6.7, read from the user-supplied Downloads attachment. The source document and referenced CSV/JSON fixtures are absent from the repository. Source SHA-256: `b63d3033db14655337158b483243efaa831b35e2f8cf03847e213d075ce75391`. See [../AGENTS.md](../AGENTS.md) for full rules, definitions and quality requirements.

**Only `POST /api/v1/authorisations` is an explicitly specified REST method/path in the case study (FR-2).** Its required minimum response is specified in section 6.3. All other methods, paths, field spellings beyond named source fields, wrappers, authentication header names, pagination, error schemas and status codes below are project proposals implementing required capabilities. They are not official HCLTech endpoint requirements. JWT is a project requirement, not a case-study protocol mandate. Health/predict operations support the chosen FastAPI architecture; the case study does not prescribe separate AI routes.

These proposals provide a common starting point for the three modules. Implement unchanged, settled portions only when the user's implementation task authorizes it. Resolve the decision register entries before implementing affected behavior. A module agent cannot independently alter this shared file; contract edits require explicit shared-file authorization. Java 17 is mandatory despite stale Java 21 references in the initial README/architecture document.

## Transport authorization and common types

All public API routes below are served by Spring Boot. React uses JSON over HTTP and user JWTs; use HTTPS outside local development. Spring Boot alone persists application records in PostgreSQL. FastAPI is an internal service, inaccessible directly to browsers/gateways, and never writes application data. Spring Boot constructs validated features from stored history; the browser/gateway cannot supply authoritative computed features or decisions.

Proposed user authentication: `Authorization: Bearer <JWT>`, with server-validated subject and roles `CARDHOLDER`, `FRAUD_ANALYST`, `FRAUD_RISK_MANAGER`. Gateway auth: `X-API-Key`, secret read from environment, accepted **only** at the authorisation route. User JWT alone must not authorize that route; gateway key must not authorize user-facing routes. Internal FastAPI auth proposal: `X-Internal-API-Key` from backend/AI environments; the separate secret must never reach React. Exact variable names will be introduced in a separately authorized configuration task.

Common conventions (proposed unless explicitly attributed):

- JSON timestamps use ISO-8601; transaction input and batch SLA output require explicit `+05:30` (IST). Response timestamps use `+05:30` for consistency. Decisions are `APPROVE | STEP_UP | BLOCK`; transaction statuses `RECEIVED | APPROVED | STEP_UP_PENDING | BLOCKED`; these enums are not interchangeable.
- Monetary API values are decimal strings with at most two fractional digits, e.g. `"15000.00"`, parsed into decimal-safe values. This is a proposed wire encoding of source decimal requirements. Ratios/scores/distances use finite JSON numbers or null; never NaN/Infinity. CSV empty fields map to JSON null, never fabricated zeros.
- IDs are opaque strings except source txn_id pattern. No case-study format is asserted for card_id, merchant_id, alert_id, step_up_id, proposal_id or audit_id. Resolve lookup relationships server-side.
- JSON error body: `{ "code": "VALIDATION_ERROR", "message": "Invalid transaction", "field_errors": [{ "field": "amount", "message": "Must be greater than zero" }], "request_id": "opaque-id" }`. `field_errors` is empty for nonvalidation errors. Never leak stack traces, credentials, full PAN or model internals in cardholder errors.
- Common statuses inherited by **every** applicable endpoint: 400/422 invalid fields (source FR-2 permits either; project proposal uses 422 for semantic validation and 400 for malformed JSON/query); 401 missing/invalid/expired relevant credentials; 403 authenticated wrong role or prohibited self-approval; 404 unknown or foreign owned resource (proposed uniform response to avoid disclosing existence); 409 conflicting state/concurrency; 500 unexpected internal failure with sanitized body. List validation uses the same error body. Endpoint entries add specific success/error codes below.
- Source requires a cardholder calling an analyst route to receive 403. Own-resource filters are enforced server-side at query and action time; clients cannot select another customer by a request field. Analyst assignment and maker identity always come from the authenticated principal.
- Proposed pagination for list routes: `page` zero-based (default 0), `size` 1–100 (default 20); response `{ "items": [...], "page": 0, "size": 20, "total": 1 }`. Date filters use `from` inclusive and `to` exclusive with `from < to`; dashboard uses IST. A chosen size limit is a design proposal, not a source rule threshold.
- None means no request body; GET requests have no JSON body. Unknown body fields are rejected by the proposed typed schemas. Request IDs are diagnostics, never idempotency keys.

### Shared response schemas

Field lists below define object shapes; a nullable type is explicitly marked `null`. All arrays may be empty. Security-sensitive variants must have separate DTOs rather than relying on the frontend to hide fields.

| Schema | Fields and exposure |
| --- | --- |
| Identity | `user_id: string`, `username: string`, `roles: string[]`; current principal only |
| CardView | `card_id`, `masked_card_number` (e.g. XXXXXXXXXXXX1234), `card_type` (DEBIT/CREDIT), `card_status` (ACTIVE/BLOCKED/EXPIRED), `expiry_date: date`, `daily_limit: money`, `international_enabled: boolean`; no full PAN/hash |
| CardholderTransaction | `txn_id`, `card_id`, `txn_ts`, `amount: money`, `currency: INR`, `merchant_id`, `merchant_name: string` (untrusted display text), `channel`, `txn_city`, `txn_country`, `status`, `display_reason: string`; **no** rule_risk_score, raw reason_codes, features, fraud_score, top_factors, model_version or alert identifiers |
| PendingStepUp | `step_up_id`, `txn_id`, `card_id`, `amount: money`, `merchant_name`, `created_at`, `expires_at`, `status: PENDING`; no OTP, scores or alerts |
| AlertSummary | `alert_id`, `txn_id`, `amount: money`, `alert_priority: HIGH/MEDIUM`, `status`, `created_at`, `sla_due_at`, `sla_breached: boolean`, `claimed_by: string/null` |
| FeaturesUsed | `velocity_10m: integer/null`, `approved_today: money/null`, `avg_amount_30d: money/null`, `speed_kmph: number/null`, `new_merchant_flag: boolean/null`, plus AI features when evaluated; include a configuration version/snapshot reference (proposed) |
| TopFactor | `feature: string`, `explanation: string`; optional signed `contribution: number` when the chosen explainer supports it. Exactly three on successful scoring; no invented values |
| AlertEvidence | `alert: AlertSummary`, `transaction: object` containing transaction fields/status and masked card reference, `card: CardView`, `rule_decision`, `decision`, `reason_codes: string[]`, `rule_risk_score: integer/null`, `features_used: FeaturesUsed`, `fraud_score: number/null`, `top_factors: TopFactor[]`, `model_status`, `model_version: string/null`, `proposal: ManagerProposal/null`; history fetched separately |
| ManagerProposal | `proposal_id`, `alert_id`, `proposed_by`, `proposed_at`, `proposed_resolution: FALSE_POSITIVE`, `comment`, `status: PENDING/APPROVED/REJECTED`, `checked_by: string/null`, `checked_at: timestamp/null`, `checker_comment: string/null` |
| AuditEvent | `audit_id`, `entity` (TRANSACTION/ALERT/CARD/CONFIGURATION), `entity_id`, `actor`, `role`, `timestamp`, `action`, `from_status: string/null`, `to_status: string/null`, `comment: string/null`, `before: object/null`, `after: object/null`; append-only, sanitized, chronological |

Cardholder DTOs deliberately omit analyst evidence. A cardholder gets neutral plain-language reasons. The source explicitly requires gateway reason codes and AI-track response fields; never reuse that privileged response as an own-transaction DTO.

## Gateway authorisation

### G1 POST /api/v1/authorisations — official method and path

**Service:** Spring Boot. **Authorized actor:** simulated gateway API key only. **Requirement:** FR-1–FR-5, FR-8, section 4 input, sections 6.1/6.1.2, AI-2, section 6.3 and NFR idempotency/concurrency/latency.

Request (all fields required; source names):

```json
{
  "txn_id": "T200000007",
  "card_id": "C1007",
  "txn_ts": "2026-03-04T14:05:00+05:30",
  "amount": "15000.00",
  "currency": "INR",
  "merchant_id": "M00451",
  "mcc": "5732",
  "channel": "ECOM",
  "txn_city": "Mumbai",
  "txn_country": "IN"
}
```

Validation: txn_id `^T[0-9]{9}$`; card/merchant references exist; ISO-8601 txn_ts with +05:30; 0 < amount ≤ ₹1,000,000 and at most two decimals; INR only; mcc exactly four digits; channel POS/ECOM/ATM/CONTACTLESS; city exists and country matches city reference. Expired/blocked cards, international-disabled and blacklisted merchants are valid fraud-policy inputs evaluated by rules, not validation errors. Merchant/MCC cross-field matching policy beyond source field validation remains unresolved. No caller-supplied risk, history, ownership, final status or score is accepted.

Response 200 (proposed success status; required minimum fields shown):

```json
{
  "txn_id": "T200000007",
  "decision": "STEP_UP",
  "reason_codes": ["AMOUNT_SPIKE"],
  "rule_risk_score": 30,
  "status": "STEP_UP_PENDING",
  "alert_id": null,
  "processed_at": "2026-03-04T14:05:00+05:30",
  "fraud_score": null,
  "top_factors": [],
  "model_status": "MODEL_UNAVAILABLE"
}
```

The timestamp and unavailable score illustrate the schema, not a measured execution. AI-track success adds finite fraud_score [0,1] and exactly three TopFactor items. `MODEL_UNAVAILABLE` is source-defined; `AVAILABLE` and `NOT_EVALUATED` are proposed additional model_status values requiring agreement. Preserve model version/feature snapshot in storage; adding them to this gateway response is not required by the source.

Idempotency: lookup txn_id before creating effects, atomically return the original decision response on a repeat, and create no duplicate transaction, alert or audit decision; repeated posting cannot increase velocity. Proposed 200 for both first/repeat responses. For a later step-up change, return the original authorization response here and the current status via own/evidence routes (project interpretation of FR-2; confirm before implementing). Do not invent payload-mismatch 409 semantics that contradict unconditional source retry behavior; retain and return the original record.

Rules: hard-stop R1/R2 outputs null rule_risk_score and null velocity/average/speed; R3–R6 block score 100 and suppress R7–R13; soft tier sums points and R13 does not force 100. Reasons ordered by rule number, model reason added only when AI tightens a decision. Deadline failure returns the normal 200 rules-only response with MODEL_UNAVAILABLE, not an API 503. BLOCK can legitimately have no alert for a policy-only decline.

Additional statuses: 400/422 invalid fields; 401 absent/wrong API key even when user JWT is supplied. Follow source 403 for forbidden user-facing calls by an authenticated gateway. Concurrency must serialize same-card feature effects and protect txn_id uniqueness, not merely avoid duplicate response objects.

## Authentication — proposed routes

### A1 POST /api/v1/auth/login

**Service:** Spring Boot. **Actor:** unauthenticated human user; no gateway login. **Request:** `{ "username": "demo-user", "password": "<entered-password>" }`. **Response 200:** `{ "access_token": "<jwt>", "token_type": "Bearer", "expires_in": 3600, "user": Identity }`; 3600 is illustrative, expiration duration is a project setting still to be agreed. Validate nonempty credentials, never log password/token, verify hashed password, derive roles on server. 401 invalid credentials with generic message; common validation/errors apply. **Source:** section 3, FR-1; JWT and exact route/fields are proposed. No real demo password is documented here.

### A2 GET /api/v1/auth/me

**Service:** Spring Boot. **Roles:** any authenticated human role. **Body:** none. **Response 200:** Identity for JWT subject. Validate token signature/expiry/subject and current permitted role state; 401 invalid/revoked identity; gateway key is insufficient. **Source:** FR-1 role enforcement; identity operation/path proposed. Refresh/logout/password-reset APIs are not specified and are not introduced by this contract.

## Cardholder — proposed routes

### C1 GET /api/v1/me/cards

**Service:** Spring Boot. **Role:** CARDHOLDER. **Body:** none; standard page/size query. **Response 200:** paginated CardView, restricted to current owner. Validate query; do not accept customer_id overrides. Common 401/403/400/422. **Source:** section 3, FR-1, card-data NFR.

### C2 GET /api/v1/me/transactions

**Service:** Spring Boot. **Role:** CARDHOLDER. **Body:** none. Query: page/size, optional `card_id`, `from`, `to`, `status`. **Response 200:** paginated CardholderTransaction, proposed newest-first `(txn_ts desc, txn_id desc)`. Validate status/date filters and card ownership (404 for foreign card); return no scores/rules/alerts. Common statuses. **Source:** section 3, FR-1, section 6.1.1.

### C3 GET /api/v1/me/transactions/{txn_id}

**Service:** Spring Boot. **Role:** CARDHOLDER owning the transaction. **Body:** none. **Response 200:** CardholderTransaction with current persisted status and neutral display_reason. Validate txn_id syntax and ownership; 404 for absent/foreign record; no gateway response reuse. **Source:** section 3, FR-1, FR-5.

### C4 GET /api/v1/me/step-ups

**Service:** Spring Boot. **Role:** CARDHOLDER. **Body:** none; page/size. **Response 200:** paginated PendingStepUp for owned cards, pending and not expired. Validate query; no OTP or risk evidence. Expiration must be processed server-side rather than silently hiding overdue pending statuses forever. **Source:** section 3, FR-5.

### C5 POST /api/v1/me/step-ups/{step_up_id}/responses

**Service:** Spring Boot. **Role:** CARDHOLDER owning the step-up/transaction. **Request:** `{ "response": "CONFIRM", "otp": "123456" }`, response CONFIRM/NOT_ME, OTP six ASCII digits retained as a string. The source requires correct OTP for CONFIRM and captures a step-up response with OTP; proposed requirement for both actions must be clarified for NOT_ME (decision D3). **Response 200:** `{ "step_up_id": "opaque", "txn_id": "T200000007", "status": "APPROVED", "card_status": "ACTIVE", "display_reason": "Transaction confirmed" }`; denial returns BLOCKED and BLOCKED card status with neutral reason. No alert identifiers or analyst codes.

Validate ownership, pending state, server expiry and OTP; CONFIRM inside 15 minutes approves; NOT_ME blocks transaction/card, adds STEP_UP_DENIED and creates a HIGH alert. Proposed 422 invalid OTP; 409 expired/already handled request, after atomically applying any due STEP_UP_TIMEOUT transition (card not automatically blocked for timeout). Scheduled expiry and cardholder response must not both win. Exact deadline boundary/OTP retry rules and whether denial requires correct OTP remain unresolved. **Source:** FR-5, section 6.1.1/6.1.2, worked example 11.

### C6 POST /api/v1/me/cards/{card_id}/block

**Service:** Spring Boot. **Role:** owning CARDHOLDER. **Request:** `{}`; optional comment not introduced. **Response 200:** CardView with card_status BLOCKED. Validate ownership/current card; repeated already-blocked action proposed as idempotent 200 without redundant status-change audit. Behavior for expired card requires agreement (D10). Record an audit transition for a real block. No unblock route. **Source:** section 3, FR-8, out-of-scope section 6.7.

## Fraud Analyst — proposed routes

### F1 GET /api/v1/analyst/alerts

**Service:** Spring Boot. **Role:** FRAUD_ANALYST. **Body:** none. Query: page/size, optional `status`, `priority`, `claimed_by=me`, `sla_breached=true|false`. **Response 200:** paginated AlertSummary sorted HIGH before MEDIUM then sla_due_at ascending; proposed alert_id tie-break for stable pages. Queue must surface OPEN and in-progress work without excluding overdue alerts. Validate enums and current user filter; breached is derived from time and nonfinal status, not an independent status enum. Common statuses. **Source:** section 3, FR-6, sections 6.1.1/6.1.2 and NFR new-alert visibility ≤5 seconds.

### F2 POST /api/v1/analyst/alerts/{alert_id}/claim

**Service:** Spring Boot. **Role:** FRAUD_ANALYST. **Request:** `{}`. **Response 200:** AlertSummary with IN_REVIEW and claimed_by from current user. Validate existence and OPEN state; atomically claim; 409 if another claim won or state is no longer OPEN (including retry, proposed); 404 missing. Never accept an analyst ID from client. **Source:** FR-6, concurrency NFR. Claim reassignment/unclaim is not defined and has no endpoint here.

### F3 GET /api/v1/analyst/alerts/{alert_id}

**Service:** Spring Boot. **Role:** FRAUD_ANALYST. **Body:** none. **Response 200:** AlertEvidence including actual features used, triggered rules, fraud score as probability (UI shows percent), top three explanations, model availability/version and current proposal. Validate alert existence; 404 absent. Read evidence need not claim first (proposal). Sanitize untrusted merchant/comment display text; show AI score unavailable on fallback. **Source:** section 3, FR-3/FR-6, AI-2 and responsible AI.

### F4 GET /api/v1/analyst/alerts/{alert_id}/history

**Service:** Spring Boot. **Role:** FRAUD_ANALYST. **Body:** none. Query: page/size and optional from/to. **Response 200:** paginated transaction objects for the alert's card, containing txn_id, txn_ts, amount, merchant_id, channel, txn_city, txn_country, status and reason_codes where available; no full PAN. Resolve the card from the alert server-side; validate date/query and alert existence; 404 absent. Proposed newest-first ordering with txn_id tie-break. **Source:** section 3, FR-3/FR-6. Prior-history evidence can include seeded records without fabricated decisions/explanations.

### F5 POST /api/v1/analyst/alerts/{alert_id}/resolutions

**Service:** Spring Boot. **Role:** FRAUD_ANALYST who owns the claim. **Request:** `{ "resolution": "FALSE_POSITIVE", "comment": "Reviewed with cardholder" }`; enum CONFIRMED_FRAUD/FALSE_POSITIVE. **Response 200:** `{ "alert": AlertSummary, "proposal": ManagerProposal/null }`.

Validate IN_REVIEW, current claim owner and comment at least 10 characters (proposed trimmed-length validation); 422 short comment; 403 nonowner; 409 stale/final/competing close. FALSE_POSITIVE amount > ₹100,000 creates PENDING_MANAGER_APPROVAL with current user as immutable maker, not a final release. Amount exactly ₹100,000 can finalize FALSE_POSITIVE. CONFIRMED_FRAUD blocks the card; final FALSE_POSITIVE logs a notification and stores feedback. Each transition and card change is atomic/audited. Do not approve or unblock the original final transaction/card on false-positive closure. **Source:** section 3, FR-6/FR-7/FR-8, lifecycle and worked example 12.

## Fraud Risk Manager — proposed routes

### M1 GET /api/v1/manager/dashboard

**Service:** Spring Boot. **Role:** FRAUD_RISK_MANAGER. **Body:** none. Query: optional from/to date range. **Response 200:** `{ "from": "timestamp-or-null", "to": "timestamp-or-null", "transactions_by_decision": { "APPROVE": 0, "STEP_UP": 0, "BLOCK": 0 }, "block_rate": 0.0, "step_up_rate": 0.0, "open_alerts_by_priority": { "HIGH": 0, "MEDIUM": 0 }, "sla_breaches": 0, "false_positive_rate": null, "top_reason_codes": [{ "reason_code": "IMPOSSIBLE_TRAVEL", "count": 1 }], "generated_at": "timestamp" }`. Shape only: example counts are not a computed report.

Validate from/to and permission; derive figures from database. False-positive rate is final FALSE_POSITIVE / closed alerts (final FALSE_POSITIVE + CONFIRMED_FRAUD); proposed null with no closed alerts. Rates numeric 0–1; top five codes. The source leaves original-vs-current decisions, date attribution and in-progress “open” inclusion ambiguous; agree D6 before implementation. Auto-refresh SHOULD, not mandatory server push. Common statuses. **Source:** FR-9.

### M2 GET /api/v1/manager/false-positive-proposals

**Service:** Spring Boot. **Role:** FRAUD_RISK_MANAGER. **Body:** none; page/size, optional status PENDING/APPROVED/REJECTED. **Response 200:** paginated `{ "proposal": ManagerProposal, "alert": AlertSummary }`, proposed pending-first/proposed_at ascending. Validate filters, return high-value FALSE_POSITIVE proposals, include maker identity; self-made proposal visibility does not permit self-approval. **Source:** section 3, FR-7 and worked example 12.

### M3 GET /api/v1/manager/false-positive-proposals/{proposal_id}

**Service:** Spring Boot. **Role:** FRAUD_RISK_MANAGER. **Body:** none. **Response 200:** `{ "proposal": ManagerProposal, "evidence": AlertEvidence }`. Validate existence; 404 absent; no claim mutation. Manager needs evidence for an informed check; exact route is proposed, not source-specified. **Source:** section 3, FR-7, human oversight.

### M4 POST /api/v1/manager/false-positive-proposals/{proposal_id}/decisions

**Service:** Spring Boot. **Role:** FRAUD_RISK_MANAGER different from proposed_by. **Request:** `{ "decision": "APPROVE", "comment": "Evidence reviewed independently" }`, enum APPROVE/REJECT. **Response 200:** `{ "proposal": ManagerProposal, "alert": AlertSummary }`.

Validate pending proposal, alert PENDING_MANAGER_APPROVAL, maker != current user; 403 self-check even for a dual-role principal; 409 already decided/concurrent check; 422 invalid decision/comment. Source captures manager comment but specifies no manager minimum length; proposed nonblank comment, without importing analyst's 10-character minimum. APPROVE finalizes FALSE_POSITIVE and logs notification. REJECT -> CONFIRMED_FRAUD with card block is **proposed**, inferred from available lifecycle next states, not explicitly stated; settle D2 first. Atomic audit/status/card side effects; neither action rewrites the transaction as APPROVED nor unblocks it. **Source:** section 3, FR-7/FR-8, section 6.1.1, example 12.

### M5 GET /api/v1/manager/audit-trails/{entity_type}/{entity_id}

**Service:** Spring Boot. **Role:** FRAUD_RISK_MANAGER. **Body:** none; page/size. **Response 200:** paginated AuditEvent ordered timestamp ascending with audit_id tie-break (proposed). Validate entity_type transaction/alert/card/configuration, target existence and access; 404 unknown. A transaction view must include the linked alert/card actions needed for its full ordered history; proposed inclusion must preserve actual entity identities (D7). No audit mutation APIs. **Source:** FR-8, section 3 and configuration auditing.

### M6 GET /api/v1/manager/audit-trails/{entity_type}/{entity_id}/export

**Service:** Spring Boot. **Role:** FRAUD_RISK_MANAGER. **Body:** none. **Response 200:** `text/csv`, attachment, one row per event in chronological order. Entity types transaction/alert/card as specified by source outputs; proposed columns `entity,entity_id,actor,role,timestamp,action,from_status,to_status,comment,before,after`, nested before/after serialized as escaped JSON cells. Exact export schema is not official. Validate entity/type/access; 404 absent. Preserve audit meaning, prevent spreadsheet formula execution when exporting free text, omit secrets/PAN/OTP. Common validation/auth statuses. **Source:** section 6.3 audit export, FR-8; finalize D7 before writing shared export consumers.

### M7 GET /api/v1/manager/rule-configuration

**Service:** Spring Boot. **Role:** FRAUD_RISK_MANAGER. **Body:** none. **Response 200:** `{ "version": "opaque-version", "settings": [{ "key": "source-config-key", "value": "typed-value" }], "updated_at": "timestamp" }`; values are JSON numbers/strings/booleans/arrays/objects as defined by a future schema derived from the actual source JSON, not all strings. Return effective thresholds/windows/points/MCC list/SLA/maker-checker/AI thresholds. Do not expose application secrets. **Source:** section 3, FR-4, configurability NFR; exact payload proposed and blocked on absent configuration file.

### M8 PUT /api/v1/manager/rule-configuration

**Service:** Spring Boot. **Role:** FRAUD_RISK_MANAGER. **Request:** `{ "expected_version": "opaque-version", "settings": [{ "key": "source-config-key", "value": "typed-value" }], "comment": "Reason for change" }`; full replacement of the agreed settings set, proposed optimistic concurrency. **Response 200:** same effective configuration object as M7 with new version. Validate allowed source keys/types, completeness, nonnegative windows/points, valid ranges, MCC format, ordering of AI thresholds and maker-checker/SLA/expiry constraints against the approved schema. Exact constraints must not be fabricated before reading source JSON. 409 stale version, 422 invalid configuration. Atomically persist config and before/after audit, and make later decisions observe a coherent configuration without a code release. Do not alter historical feature/config snapshots. Manager comment nonblank is a project proposal. **Source:** FR-4/FR-8, section 3 and 6.1 configurability. Versioned rule-edit approval is a MAY bonus, not implied by this minimal configuration API.

## Internal AI service — proposed routes

### I1 GET /health

**Service:** FastAPI. **Actor:** internal backend/operator with proposed internal service credential; not end-user roles. **Body:** none. **Response 200:** `{ "status": "ready", "model_version": "version-id" }`. **Response 503:** `{ "status": "not_ready", "model_version": null }` when no usable model. Do not leak file paths, training records or secrets. 401 missing/wrong internal key; no patient/customer data involved. Model readiness rather than mere process liveness is the proposed meaning; separate liveness can be designed later if needed. **Source:** supports AI-2/fallback; health operation and security are architectural proposals.

### I2 POST /internal/v1/predictions

**Service:** FastAPI. **Actor:** Spring Boot internal service credential only. **Requirement:** AI-1/AI-2, section 6.1.2 model features, responsible AI and 150 ms fallback budget. **Request:** typed feature object:

```json
{
  "features": {
    "card_type": "DEBIT",
    "channel": "ECOM",
    "mcc": "5732",
    "mcc_category": "reference-category",
    "merchant_risk": "LOW",
    "amount": "15000.00",
    "local_hour": 14,
    "is_international": false,
    "country_risk_high": false,
    "distance_from_home_km": 0.0,
    "velocity_10m": 0,
    "velocity_24h": 0,
    "amount_to_avg30_ratio": 6.25,
    "new_merchant_flag": false,
    "card_age_days": 365
  }
}
```

This is a schema illustration, not a source row (card_age_days/category are illustrative). Inputs required as keys; only amount_to_avg30_ratio may be null for absent approved history. Validate finite/nonnegative distance/count/ratio/age, local_hour integer 0–23, four-digit mcc, allowed card_type/channel/merchant_risk, source-supported mcc_category, amount range and types. card_age_days negative/missing-reference behavior must be resolved against real data (D10), not silently imputed. Model preprocessor must use the training feature definitions and documented unknown-category policy. No txn_id, txn_ts, city_tier, PAN, names, mobile numbers or raw merchant text as predictive inputs. Internal request tracing can use a nonfeature header rather than a forbidden feature field.

**Response 200:**

```json
{
  "fraud_score": 0.82,
  "top_factors": [
    { "feature": "amount_to_avg30_ratio", "explanation": "Amount is 6.25 times the card's 30-day average" },
    { "feature": "channel", "explanation": "E-commerce channel contributed to model risk" },
    { "feature": "amount", "explanation": "Transaction amount contributed to model risk" }
  ],
  "model_version": "version-id"
}
```

Score/factors are illustrative and must be computed by the real model/explainer. Validate probability finite [0,1], exactly three valid feature explanations, model_version nonempty. SHAP/equivalent explanations must match actual inference; no generated claims of causality or fabricated factors. Proposed 422 input schema failure, 503 unavailable/loading model, 500 inference failure. No final_decision field: Spring Boot owns orchestration. Logging must avoid identifiers/features containing personal data and must never log PAN.

Backend applies a **total 150 ms model-service budget**, including transport and any explanation work. No retries that exceed it. Failure, timeout or proposed invalid-output rejection returns the rules-only decision with MODEL_UNAVAILABLE and null fraud_score/empty top_factors; persist the unavailability state and show the analyst message. A 200 API authorization response with fallback is distinct from FastAPI 503. Overall source performance target remains p95 300 ms with model, 150 ms rules-only. Model service does not decide final status, close alerts or change cards.

## Business behavior shared by the interfaces

### Decision rules and AI policy

The exact R1–R13 matrix, score/null semantics and definitions are in [../AGENTS.md](../AGENTS.md). Do not sum blocking rules into soft scores or evaluate soft rules after R3–R6. Use unrounded values and configurable thresholds; keep rules-only decision distinct from AI-influenced decision.

AI-2 explicitly states rules APPROVE + score ≥0.70 -> STEP_UP, and rules STEP_UP + score ≥0.90 -> BLOCK, adding HIGH_FRAUD_SCORE_MODEL on escalation. It never relaxes BLOCK. Direct or chained APPROVE -> BLOCK at ≥0.90 is not explicitly specified; D1 must be settled. Model features, model_version and configuration used must be reproducible. Cardholder views omit all AI/rule internals.

### Transaction step-up alert and review lifecycle

Transactions: RECEIVED -> APPROVED/STEP_UP_PENDING/BLOCKED; STEP_UP_PENDING -> APPROVED/BLOCKED. APPROVED and BLOCKED are final. CONFIRM with correct OTP in 15 minutes approves; NOT_ME blocks with STEP_UP_DENIED and blocks the card; expiry blocks with STEP_UP_TIMEOUT without automatic card block.

Create one alert only for a BLOCKED transaction with a fraud-suspicion code: MERCHANT_BLACKLISTED, VELOCITY_BURST, IMPOSSIBLE_TRAVEL, MULTIPLE_RED_FLAGS, STEP_UP_DENIED, STEP_UP_TIMEOUT, HIGH_FRAUD_SCORE_MODEL. Policy-only CARD_NOT_ACTIVE/INTL_NOT_ENABLED/DAILY_LIMIT_EXCEEDED create none. STEP_UP_PENDING alone creates none. HIGH when amount ≥₹50,000, IMPOSSIBLE_TRAVEL/MULTIPLE_RED_FLAGS/STEP_UP_DENIED, or AI score ≥0.90; otherwise MEDIUM. Due: creation +30 minutes HIGH or +240 minutes MEDIUM; batch creation is txn_ts. SLA_BREACHED is a flag on unclosed overdue alerts.

Alerts: OPEN -> IN_REVIEW -> CONFIRMED_FRAUD/FALSE_POSITIVE/PENDING_MANAGER_APPROVAL; pending manager -> FALSE_POSITIVE/CONFIRMED_FRAUD. Analyst comment minimum 10 characters; false positive >₹100,000 needs distinct manager checker. Card block on confirmed fraud; logged cardholder notification on final false positive. Do not change final transaction statuses during alert review. Audit each real transition with no mutable audit endpoint. Source section 6.1.2's optional heading does not override FR-6 expected-alert acceptance or AI-2's explicit feature references.

## Non-REST interfaces and acceptance

These required behaviors are not invented REST endpoints:

- Batch command (FR-10) reads a file sorted txn_ts then txn_id, uses freshly loaded history per batch, and writes rules-only `output/decisions_output.csv` and `output/alerts_output.csv`. Provide seed/reset command; do not add a public reset endpoint. Replay-to-API mode at configurable rate is SHOULD. Sample expectations are 150 rows and 24 alerts; evaluation has 306 rows on 2026-03-05. Fixtures must be supplied before verifying them.
- Decisions CSV columns exactly: `txn_id,decision,reason_codes,rule_risk_score,velocity_10m,avg_amount_30d,speed_kmph`. Reason codes pipe-separated, score/velocity whole numbers, average/speed two decimals; hard-stop empty fields per source.
- Alerts CSV columns exactly: `txn_id,alert_priority,sla_due_at`, time offset +05:30. Keep this rules-only output separate from AI-influenced live alerts.
- AI scores CSV: `txn_id,fraud_score,final_decision,top_factors`, score four decimals, three feature names pipe-separated. AI evaluation report/model card/training are filesystem deliverables, not inference endpoint fields.
- Simulator screen uses the authorization fields, but browser exposure of a gateway key is prohibited. Decide D8 before implementing its submission path; no unapproved JWT-to-gateway proxy endpoint is implied.
- Scheduled step-up expiration, notification/OTP simulation logs, audit append and configuration seeding are internal behaviors, not public mutation endpoints. Real OTP/SMS/email/network integration and card unblock are out of scope.
- Validate 150 sample rules/feature outputs and 24 alerts against official fixtures, plus examples 1–14. Key boundaries: amount ₹12,000 at average ₹2,400 does not trigger R8; speed example 5 is 1148.09; example 10 soft score is 80 even though R13 blocks. Example 13 APPROVE +0.82 -> STEP_UP; example 14 STEP_UP +0.93 -> BLOCK.
- Test 403 role isolation, foreign-resource protection, keyless authorization 401, field validation 400/422, retries with no duplicate effects, concurrent claims/closes 409, consistent same-card velocity, maker/checker self-action 403, step-up expiry race, final-state protection, model unavailable/timeout and response redaction. Performance: p95 ≤300 ms including model (150 ms rules-only), 300-row batch ≤60 seconds, analyst alert visibility ≤5 seconds.

## Requirement coverage

| Case-study requirement | Contract location |
| --- | --- |
| FR-1 authentication/roles | Transport, G1, A1/A2 and each role group |
| FR-2 authorisation/idempotency | G1 and common validation |
| FR-3 stored-history features | G1, F3/F4, I2 and AGENTS definitions |
| FR-4 exact configurable rules | G1, M7/M8 and AGENTS rule matrix |
| FR-5 step-up | C4/C5 and scheduled expiry |
| FR-6 alerts/claims/resolution | F1–F5 and shared lifecycle |
| FR-7 maker-checker | F5, M2–M4 |
| FR-8 audit/configuration changes | M5/M6/M8 and all mutation side effects |
| FR-9 dashboard | M1 |
| FR-10 batch/replay/reset | Non-REST interfaces |
| AI-1 training/baseline/evaluation | AGENTS AI section; no public training route |
| AI-2 scoring/explanations/fallback | I1/I2, G1, F3 and escalation policy |
| AI-3 threshold/segment evaluation | AGENTS AI section; model card/evaluation artifacts |
| AI-4 SHOULD second detector | Future authorized contract extension; not assumed present |
| AI-5 MAY narrative | Future authorized contract extension with guardrails; not assumed present |

## Decision register and missing evidence

These are disclosed gaps, not authorization to invent business policy. Agree unresolved behavior and update this shared contract with explicit permission before affected implementation.

| ID | Question or missing evidence | Boundary / next action |
| --- | --- | --- |
| D1 | AI score ≥0.90 on original rules APPROVE: one-step escalation to STEP_UP or chained to BLOCK? Hard-stop inference/model_status behavior is also unspecified. | Confirm original-decision lookup semantics; do not invent a direct BLOCK. Decide NOT_EVALUATED/AVAILABLE enums and hard-stop scoring/nulls jointly with backend/AI. |
| D2 | Manager REJECT outcome is not explicitly stated; lifecycle permits only FALSE_POSITIVE or CONFIRMED_FRAUD next. | Proposed REJECT -> CONFIRMED_FRAUD needs agreement because it blocks a card. False-positive closure does not authorize transaction reapproval/card unblock. |
| D3 | Must NOT_ME use correct OTP? Exact expiry equality, OTP retry limits and simultaneous response/expiry behavior? | Correct OTP for CONFIRM and 15-minute expiry are official. Resolve remaining details; ensure one atomic winning transition. |
| D4 | Exact inclusive lower boundary of history windows; equal timestamps with strict-prior rule and zero-time speed sentinel; late-arriving earlier activity? | Use strict-prior semantics. Verify boundary cases/configuration/sample data and clarify any unresolved interpretation. |
| D5 | Source config JSON schema, reference categories, actual fixtures/model data absent. | Obtain authorized access; do not invent configuration keys or report sample parity/model metrics. Source document is not vendored in repository either. |
| D6 | Dashboard decision counts before/after step-up/AI; date attribution; definition of open alerts and zero denominators. | Agree aggregation semantics so counts/rates reconcile; proposed null false-positive rate when denominator zero. |
| D7 | Audit export exact columns and transaction-to-linked-entity history traversal unspecified. | Agree proposed CSV and linked-view semantics; always preserve append-only provenance and redact sensitive values. |
| D8 | Simulator submission must not expose privileged gateway key or broaden gateway access. | Agree a trusted local replay/gateway adapter or separately designed restricted simulation interface; no secret in VITE_ config. |
| D9 | JWT expiry/revocation, internal service credential variables, enum/status details, decimal wire encoding and errors are project proposals. | Coordinate frontend/backend/AI before integration; no module agent may silently alter payloads. |
| D10 | Unknown reference/category, negative card age, inconsistent merchant/MCC, expired-card block request and unavailable-history policies not fully specified. | Validate official pack first; agree errors/handling without converting valid policy declines into malformed-input errors. |
| D11 | Does authorization retry after later step-up return the original response snapshot or updated outcome? | FR-2 says original decision; proposed original snapshot here with current state from read APIs. Confirm before implementing. |
| D12 | Existing README/architecture say Java 21; case-study source delivery path `/code` differs from root module layout. | Java 17 is mandatory now. Separate authorization needed to correct protected files and design final submission packaging. |

No application code, database schema, dependency installation, commit or push is authorized by this documentation deliverable.
