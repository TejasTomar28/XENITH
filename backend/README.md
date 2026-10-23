# FraudShield backend

This module contains the Java 17 Spring Boot backend for FraudShield. Milestone 01 provides database-backed users, stateless JWT authentication, role boundaries, gateway API-key enforcement and structured API errors. Milestone 02 adds the PostgreSQL persistence model, JPA repositories, official-data importer and evaluation reset support. Milestone 03 adds stored-history feature calculation and the complete rules-only R1-R13 evaluator. The authorization API and transaction lifecycle orchestration remain intentionally unimplemented.

## Requirements

- Java 17
- Maven 3.9+
- PostgreSQL 14+
- Docker only when running the Testcontainers integration suite

## Required configuration

Set secrets in the environment. Do not commit their values.

| Variable | Purpose |
| --- | --- |
| `DB_URL` | PostgreSQL JDBC URL, default `jdbc:postgresql://localhost:5432/fraudshield` |
| `DB_USERNAME` | Database user, default `fraudshield` |
| `DB_PASSWORD` | Database password; no secret default |
| `JWT_SECRET` | JWT HMAC secret, at least 32 characters |
| `JWT_ISSUER` | Token issuer, default `fraudshield-backend` |
| `JWT_EXPIRATION_SECONDS` | Token lifetime, 60–86400 seconds; default 3600 |
| `GATEWAY_API_KEY` | Simulated gateway key, at least 24 characters |

Start the application from this directory after PostgreSQL is ready:

```bash
mvn spring-boot:run
```

Flyway creates the schema and Hibernate validates it. The application never uses `ddl-auto=update`.

## Development accounts

There is no public registration API. A development-only seeder is available only with the `dev` profile and an explicit enable flag. It creates one account for each supported role and stores BCrypt hashes.

```bash
export SPRING_PROFILES_ACTIVE=dev
export DEV_USERS_ENABLED=true
export DEV_CARDHOLDER_PASSWORD='set-a-local-password'
export DEV_ANALYST_PASSWORD='set-a-local-password'
export DEV_MANAGER_PASSWORD='set-a-local-password'
```

The default development usernames are `cardholder`, `analyst` and `manager`. Override them with `DEV_CARDHOLDER_USERNAME`, `DEV_ANALYST_USERNAME` and `DEV_MANAGER_USERNAME`. Missing development passwords stop startup when seeding is enabled. Production configuration neither enables nor supplies development accounts.

## Endpoints in this milestone

- `GET /api/v1/health` is public and returns JSON.
- `POST /api/v1/auth/login` returns a signed bearer token for an enabled user.
- `GET /api/v1/auth/me` returns the authenticated identity.
- `POST /api/v1/authorisations` has no controller yet. The security chain already requires `X-API-Key`; a valid gateway request reaches the application and receives 404 until transaction processing is implemented.

Future `/api/v1/me/**`, `/api/v1/analyst/**` and `/api/v1/manager/**` routes are restricted to their matching roles. Resource ownership checks will be added with the related domain entities.

## Persistence model

Flyway V2 creates reference tables for countries, cities and merchant category codes; identity/card tables for customers and cards; merchant and transaction tables; feature, reason-code and model-prediction records; step-up, alert and manager-approval workflow records; notification outbox records; versioned rule configuration; append-only audit events; and import execution metadata.

Transaction timestamps use PostgreSQL `TIMESTAMPTZ` and Java `OffsetDateTime`. Money uses `NUMERIC` and `BigDecimal`. Card records contain only a BCrypt hash and the final four digits of the synthetic card number. Mutable cards, transactions, step-up requests, alerts and manager proposals have version columns. Card and alert repositories also expose pessimistic locking queries for future transactional workflows.

The transaction repository provides strict-prior queries for velocity, same-IST-day approved spend, 30-day approved average, the most recent approved physical transaction and 90-day merchant familiarity. Ownership queries traverse the authenticated user's customer and card relationships.

## Rules-only fraud evaluation

`FraudEvaluationService.evaluate(TransactionEvaluationRequest)` is the internal Milestone 03 entry point. It loads the card first and evaluates R1/R2 without reading historical features. For non-hard-stop requests it loads reference data and the active persisted configuration, calculates stored-history and AI-compatible features, then evaluates R3-R13 in official rule order.

The result contains the rules-only decision, nullable hard-stop score, ordered reason codes, calculated features and configuration checksum. The evaluator does not persist the current request, mutate lifecycle status, create an alert, generate an OTP or call the AI service.

All runtime thresholds, windows, points and MCC lists are required in the active configuration JSON. Missing or malformed settings stop evaluation explicitly. There are no threshold defaults in application code. The currently supported normalized keys are exercised by the configuration tests; compatibility with the official JSON file must be confirmed when the source data pack is available.

Historical queries use a lower-inclusive, upper-exclusive window: `[current timestamp - configured window, current timestamp)`. Equal timestamps are excluded by the official strict-prior rule. Travel-speed math still implements the documented `99999.99` sentinel for a zero elapsed interval with positive distance, although a strict-prior repository lookup cannot select an equal-timestamp predecessor.

## Official dataset seed

The seed command expects these exact files in one directory:

- `case_01_country_risk_V1.0.csv`
- `case_01_cities_V1.0.csv`
- `case_01_mcc_codes_V1.0.csv`
- `case_01_customers_V1.0.csv`
- `case_01_cards_V1.0.csv`
- `case_01_merchants_V1.0.csv`
- `case_01_rules_config_V1.0.json`
- `case_01_transactions_history_V1.0.csv`

It validates headers, formats, enums and references; imports in dependency order inside one transaction; preserves source transaction instants; stores rule JSON without assuming undocumented keys; and skips existing identifiers/checksums on repeat runs. The command is available only under the `dev` or `evaluation` profile.

```bash
export FRAUDSHIELD_DATASET_CUSTOMER_SEED_PASSWORD='set-a-local-password'
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none --fraudshield.dataset.command=seed --fraudshield.dataset.directory=/absolute/path/to/data-pack"
```

The supplied dataset password is BCrypt-hashed for imported cardholder accounts. Do not put it in source control or command-line arguments.

## Evaluation reset

The reset command deletes only transactions marked `resettable`, their cascading feature, reason, prediction, step-up, alert, approval and notification records, and their resettable audit events. It restores affected cards to their seeded status and preserves users, customers, cards, merchants, reference data, configuration and historical transactions.

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=evaluation \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none --fraudshield.dataset.command=reset"
```

There is no reset HTTP endpoint. The database prevents normal updates or deletes on audit events; only the scoped reset function enables deletion during its transaction.

## Tests

```bash
mvn verify
```

JWT and rules-engine unit tests do not need infrastructure. The integration suite uses a real PostgreSQL Testcontainer to run both migrations, validate Hibernate mappings, exercise constraints/imports/history queries/reset behavior, evaluate transactions end to end and recheck Milestone 01 authentication. It is skipped when Docker is unavailable; H2 is not used.
