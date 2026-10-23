# Planned architecture

## Scope and status

This document records the initial design for FraudShield. No services, interfaces, database schemas, fraud decisions, or models have been implemented.

```text
React + Vite + Tailwind CSS
             |
             v
Java 21 + Spring Boot  ------>  Python + FastAPI + XGBoost
             |
             v
         PostgreSQL
```

## Service responsibilities

- **Frontend:** Planned transaction views, step-up verification interaction, and fraud alert management screens. The browser communicates with the backend.
- **Backend:** Planned application API, transaction workflow coordination, validation, risk scoring requests, step-up verification orchestration, and alert management. It owns access to the main PostgreSQL database.
- **AI service:** Planned internal inference API that accepts agreed transaction features and returns a model risk score. Model training, feature definitions, and model versioning will be designed later.
- **PostgreSQL:** Main application database for planned transaction, verification, and alert records. Schemas and retention policies will be defined in later milestones.

## Proposed transaction flow

1. The React client sends a transaction-related request to Spring Boot.
2. Spring Boot validates the request and prepares the agreed scoring inputs.
3. Spring Boot requests a risk score from FastAPI.
4. Spring Boot applies a future decision policy, potentially initiating step-up verification or creating an alert.
5. Spring Boot persists the outcome in PostgreSQL and returns an appropriate response to the client.

Risk thresholds, fraud rules, API contracts, failure behavior, authentication, authorization, and performance targets remain to be specified. Real-time detection is a project goal; no latency guarantees exist at initialization.

## Development and configuration

Docker Compose will eventually orchestrate the frontend, backend, AI service, and PostgreSQL. The current Compose file is only a placeholder. Service URLs and local database settings are documented in `.env.example`; applications do not consume them yet.

The three developers can own frontend, backend/database, and AI workstreams respectively. Agree on contracts in `docs/requirements/` and review changes through pull requests. Use synthetic data, keep secrets out of Git, and avoid logging sensitive payment information.
