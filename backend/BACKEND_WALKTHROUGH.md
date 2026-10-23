# FraudShield backend walkthrough

## Startup and security

`FraudShieldApplication.main` starts Spring Boot, Flyway and scheduling. `SecurityConfig` assigns the public, gateway, Cardholder, Fraud Analyst and Fraud Risk Manager route boundaries. `GatewayApiKeyFilter` protects authorisation independently from `JwtAuthenticationFilter`, which validates tokens through `JwtService` and reloads the enabled database user.

## Authorisation flow

`AuthorizationController` passes a validated request to `TransactionAuthorizationService`. The service provides transaction-ID idempotency. `TransactionAuthorizationProcessor` locks the card, validates references, invokes `FraudEvaluationService`, calls `FraudModelClient` when eligible, applies AI escalation and commits the result through `AuthorizationRecordStore`.

`TransactionFeatureCalculator` derives strict-prior velocity, approved spend, 30-day average, travel speed, merchant novelty and model features. Batch calls use seeded `HISTORICAL` rows plus earlier rows of that batch source, preventing demo traffic from contaminating official output.

`FraudRulesEngine` first evaluates R1/R2, then R3–R6, then R7–R12 and R13. It preserves rule order, hard-stop null fields, strict comparisons and the capped soft score. `HttpFraudModelClient` calls FastAPI with the configured total deadline. An available model may only increase friction; timeout, invalid output or transport failure becomes `MODEL_UNAVAILABLE` and leaves the rules result unchanged.

## Step-up and fraud operations

For a live STEP_UP, `StepUpVerificationService` creates a BCrypt OTP verifier and registers restricted delivery after commit. It row-locks the step-up, transaction and card before CONFIRM, NOT_ME or expiry. Confirmation approves; denial blocks the transaction/card and creates a HIGH alert; expiry blocks only the transaction and creates the priority required by the alert policy. `StepUpExpiryScheduler` safely retries pending expirations.

`AlertReviewService` lists evidence, atomically claims OPEN alerts and lets only the owner resolve them. Confirmed fraud blocks the card. A false positive above the configured maker-checker limit creates a proposal. `ManagerService` prevents self-check and concurrent repeat decisions, serves the dashboard/configuration/audit APIs, and applies manager outcomes. `WorkflowStore` centralizes append-only audit, card block, reason and alert side effects.

## Batch and persistence

`BatchProcessingService` reads either official batch header, validates every row, rejects duplicates, sorts deterministically and reuses `TransactionAuthorizationService` plus the normal feature/rule/model path. Batch persistence keeps the official decisions and alerts rules-only while saving any model result for `fraud_scores_output.csv`. Records are marked resettable. `EvaluationResetService` removes only evaluation activity and restores affected seeded card state.

PostgreSQL constraints protect transaction IDs, one alert and step-up per transaction, maker-checker identity and lifecycle fields. Pessimistic row locks serialize same-card decisions and human workflow races. The database trigger keeps audit rows append-only outside the scoped evaluation reset.

## Example

A gateway sends an ECOM transaction with a new merchant and ₹50,000 amount. R8/R12 may produce STEP_UP. The processor writes `STEP_UP_PENDING`, evidence, reasons and model status. `StepUpVerificationService` creates and delivers the simulated OTP after commit. A correct CONFIRM changes the step-up to CONFIRMED and transaction to APPROVED in one transaction. NOT_ME instead adds `STEP_UP_DENIED`, blocks the transaction and card, creates a HIGH alert and writes ordered audit events.

## Main classes

- `FraudShieldApplication.main` — process entry point.
- `SecurityConfig.securityFilterChain` — route authorization.
- `TransactionAuthorizationService.authorize` — live idempotency boundary.
- `TransactionAuthorizationProcessor.process` — orchestration and persistence transaction.
- `TransactionFeatureCalculator.calculate` — historical features.
- `FraudRulesEngine.evaluate` — R1–R13 precedence.
- `HttpFraudModelClient.evaluate` — FastAPI call and fallback.
- `StepUpVerificationService.respond` — OTP lifecycle.
- `AlertReviewService.resolve` and `ManagerService.decide` — human review.
- `BatchProcessingService.process` — deterministic batch and official outputs.
