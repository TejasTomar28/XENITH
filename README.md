# FraudShield

AI-powered real-time card transaction fraud detection, step-up verification, and fraud alert management platform.

FraudShield is being developed for the HCLTech AI Full Stack Developer Hackathon by a team of three developers. The platform aims to help banking teams assess transaction risk, request additional verification for suspicious activity, and manage fraud alerts.

## Project status

Repository initialization only. Application code, APIs, database schemas, fraud rules, ML models, and runnable services will be introduced in later milestones.

## Technology stack

| Layer | Planned technology |
| --- | --- |
| Frontend | React, Vite, Tailwind CSS |
| Backend | Java 21, Spring Boot, Maven |
| AI service | Python, FastAPI, XGBoost |
| Database | PostgreSQL |
| Local orchestration | Docker Compose |

## Proposed architecture

The React client communicates with Spring Boot. Spring Boot coordinates transaction processing, requests risk scores from FastAPI, manages step-up verification and alerts, and persists application data in PostgreSQL. FastAPI hosts the planned XGBoost inference workflow.

See [the architecture plan](docs/architecture.md) for service boundaries and the proposed request flow. These are design intentions, not implemented capabilities.

## Repository layout

```text
backend/             Java backend workspace
frontend/            React frontend workspace
ai-service/          Python AI service workspace
data/                Approved, non-sensitive development data
docs/               Architecture and requirements documentation
output/design/       Shareable design deliverables
scripts/             Development and maintenance scripts
```

## Local configuration

Copy `.env.example` to `.env` and supply local configuration when services are implemented. The example contains placeholders only; `.env` is ignored by Git. Docker Compose currently declares no services and cannot start the application.

Never commit credentials, actual cardholder information, customer records, or production transaction data. Use synthetic or explicitly approved anonymized data for development. Generated data, model binaries, and build outputs are ignored.

## Collaboration

Use `main` as the shared integration branch. Create short-lived feature branches, keep changes scoped, and review pull requests before merging. Developers can work independently in `frontend/`, `backend/`, and `ai-service/`, agreeing on API contracts in `docs/requirements/` before implementation. Keep secrets in local configuration and document new configuration keys in `.env.example`.

# XENITH
