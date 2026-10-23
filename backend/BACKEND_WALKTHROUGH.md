# FraudShield backend walkthrough

## 1. When a customer clicks Pay

The gateway sends `POST /api/v1/authorisations`. `AuthorizationController.authorize` validates the JSON and calls `TransactionAuthorizationService.authorize`. `TransactionAuthorizationProcessor.process` locks the card row, validates reference data, calculates history, evaluates rules, calls FastAPI when eligible, applies AI escalation and asks `AuthorizationRecordStore.persist` to commit the transaction, evidence, model result, audit and optional alert atomically.

## 2. Repeated Pay clicks

The gateway must reuse the same `txn_id` when retrying one payment. `TransactionAuthorizationService.authorize` first calls `AuthorizationRecordStore.find`; an exact retry returns the immutable original response snapshot without running rules, AI or side effects again. `TransactionAuthorizationProcessor.process` repeats that check after obtaining the card lock, which closes the concurrency gap. PostgreSQL also has a unique constraint on `transactions.txn_id`; a losing cross-card race reloads the winner. Reusing a `txn_id` with different fields returns 409. Different IDs are separate payments and are serialized by the card lock; checkout-level deduplication would require a separate stable checkout ID from the caller.

## 3. JWT versus gateway API key

`SecurityConfig.securityFilterChain` permits login publicly, applies human roles to `/me`, `/analyst` and `/manager`, and reserves authorisation for `ROLE_GATEWAY`. `GatewayApiKeyFilter.doFilterInternal` compares `X-API-Key` in constant time. `JwtAuthenticationFilter` and `JwtService.validate` verify human JWT signature, issuer, expiry, subject and role, then reload the enabled user. A JWT cannot replace the gateway key, and a gateway key cannot call human APIs.

## 4. How transaction history is used

`TransactionFeatureCalculator.calculate` calls strict-prior repository queries to calculate 10-minute/24-hour velocity, IST approved spend today, approved 30-day average, six-hour physical travel speed and 90-day merchant familiarity. Pending and blocked rows count for velocity and familiarity but not approved sums or averages. `TransactionAuthorizationProcessor.process` holds a PostgreSQL card lock while calculating and saving, so concurrent payments on one card see a consistent order.

## 5. R1–R13

`FraudRulesEngine.evaluateHardStops` evaluates R1/R2 together and returns only their ordered codes with empty derived fields. `FraudRulesEngine.evaluate` then evaluates R3–R6; any match blocks with score 100 and suppresses soft rules. Otherwise it evaluates R7–R12 in order, sums configurable points up to 100, and appends R13 `MULTIPLE_RED_FLAGS` when enough soft rules fire. No soft match approves; one or more soft matches step up.

## 6. Spring Boot and FastAPI

For non-rule-blocked decisions, `HttpFraudModelClient.evaluate` sends the non-sensitive `AiFeatureVector` to `POST /internal/v1/predictions` with `X-Internal-API-Key`. It accepts only a finite score from 0 to 1, a nonblank model version and exactly three valid factors. `TransactionAuthorizationProcessor.process` may tighten APPROVE to STEP_UP at the configured lower threshold or STEP_UP to BLOCK at the upper threshold. It never relaxes a decision.

## 7. If FastAPI is down

`HttpFraudModelClient.evaluate` applies the configured deadline to connection and total completion. Connection errors, timeouts, non-200 responses, malformed JSON and invalid scores return `ModelEvaluation.unavailable`. The processor persists `MODEL_UNAVAILABLE`, no fabricated score, and the original rules-only decision.

## 8. OTP lifecycle

`StepUpVerificationService.transactionPending` creates exactly one database-backed step-up, generates six digits with `SecureRandom`, stores only a BCrypt hash, and registers `RestrictedDemoOtpLogger.send` after the authorization commits. `StepUpVerificationService.respond` locks the step-up, transaction and card. A correct CONFIRM before expiry approves the transaction. NOT_ME denies it, blocks the card, adds `STEP_UP_DENIED` and creates a HIGH alert. `StepUpExpiryScheduler.expirePending` finds overdue rows after startup and calls `expireOne`; timeout blocks the transaction, adds `STEP_UP_TIMEOUT`, creates an alert and leaves the card active.

## 9. Wrong, expired and repeated OTP responses

An incorrect CONFIRM increments `attempt_count`, appends an `OTP_FAILED` audit event and returns 422 without approving. `respond` checks the deadline itself, so a delayed scheduler cannot permit an expired OTP. The row lock lets exactly one of CONFIRM, NOT_ME or timeout finalize; later requests return 409. `expireOne` is idempotent, and database uniqueness prevents duplicate step-ups or alerts.

## 10. Analyst investigation

`AlertReviewService.alerts` returns the priority/SLA queue. `claim` locks the alert, so the first analyst moves OPEN to IN_REVIEW and concurrent claims return 409. `detail` and `history` expose evidence without PAN or OTP. `resolve` requires the assigned analyst and a trimmed comment of at least ten characters. CONFIRMED_FRAUD closes the alert and blocks the card; eligible FALSE_POSITIVE decisions close it and write an outbox notification.

## 11. Manager approval

For FALSE_POSITIVE above the configured ₹100,000 limit, `AlertReviewService.resolve` creates one PENDING proposal. `ManagerService.decide` locks the proposal and related alert, rejects self-check with 403 and lets one concurrent manager decision win. APPROVE closes FALSE_POSITIVE. The documented project interpretation maps REJECT to CONFIRMED_FRAUD and blocks the card.

## 12. PostgreSQL consistency

Spring `@Transactional` methods group status changes and their audit/alert/card effects. `CardRepository.lockById`, `StepUpVerificationService.lock`, `AlertReviewService.lockAlert` and `ManagerService.lockProposal` use database row locks. Unique constraints protect `txn_id`, one step-up, one alert, one model prediction and one proposal per transaction/alert. Flyway V5 stores the immutable authorization response used for retries, while the current lifecycle status remains separately queryable. The audit trigger blocks normal update/delete operations.

## Suggested study order

1. `SecurityConfig`
2. `TransactionAuthorizationService`
3. `TransactionAuthorizationProcessor`
4. `TransactionFeatureCalculator`
5. `FraudRulesEngine`
6. `AuthorizationRecordStore`
7. `HttpFraudModelClient`
8. `StepUpVerificationService`
9. `AlertReviewService`
10. `ManagerService`
