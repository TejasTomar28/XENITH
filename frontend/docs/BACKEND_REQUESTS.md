# Backend Integration Notes & Requests

This document outlines key coordination items and interface expectations from the Frontend module to ensure seamless integration with the Spring Boot backend service.

## 1. CORS Configuration
In local development and containerized testing, the frontend runs on port 3000 (via Vite proxy or standalone browser requests).
- **Allowed Origins**: `http://localhost:3000`, `http://127.0.0.1:3000`
- **Allowed Methods**: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`
- **Allowed Headers**: `Authorization`, `Content-Type`, `X-Requested-With`, `Accept`
- **Exposed Headers**: `Content-Disposition` (essential for CSV file downloads in `/api/v1/manager/audit-trails/{entity_type}/{entity_id}/export`)

## 2. Authentication Response Schema
Please ensure `POST /api/v1/auth/login` and `GET /api/v1/auth/me` return:
```json
{
  "access_token": "<jwt-token-string>",
  "token_type": "Bearer",
  "expires_in": 3600,
  "user": {
    "user_id": "U1001",
    "username": "cardholder1",
    "roles": ["CARDHOLDER"]
  }
}
```
*Note*: Field name must be `access_token` (snake_case, matching `docs/api-contracts.md`), not `accessToken`.

## 3. Standardized Error Response Format
To allow field-level form validation in React Hook Form, please maintain the documented error contract for 400, 422, and 409 responses:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid transaction",
  "field_errors": [
    { "field": "comment", "message": "Comment must be at least 10 characters long" }
  ],
  "request_id": "req-xyz-123"
}
```

## 4. Concurrent Claim & Close Behavior
When multiple analysts attempt to claim an open alert or resolve the same record concurrently:
- Winner: HTTP 200 with updated alert object
- Loser: HTTP 409 Conflict with sanitized message: `"Already claimed by another analyst"` or `"Alert state has already been finalized"`

## 5. Monetary Formatting
Monetary figures should consistently be formatted as two-decimal string values (e.g. `"15000.00"` or `"0.00"`). Avoid emitting raw unrounded floats or floating-point approximations.
