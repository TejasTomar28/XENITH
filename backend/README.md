# FraudShield backend

This module contains the Java 17 Spring Boot foundation for FraudShield. Milestone 01 provides PostgreSQL/Flyway startup, database-backed users, stateless JWT authentication, role boundaries, gateway API-key enforcement and structured API errors. Transaction processing and fraud rules are intentionally not implemented yet.

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

## Tests

```bash
mvn verify
```

JWT unit tests do not need infrastructure. The integration suite uses a real PostgreSQL Testcontainer and is skipped when Docker is unavailable; H2 is not used.
