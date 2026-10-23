# FraudShield Frontend - Architectural & Integration Assumptions

This document outlines frontend-specific design choices, conventions, and operational assumptions aligned with `docs/api-contracts.md` and `AGENTS.md`.

## 1. Authentication & Session Management
- **Token Format**: Bearer JWT received in `POST /api/v1/auth/login` under `{ "access_token": "<jwt>", "token_type": "Bearer", ... }`.
- **Storage Strategy**: Primary token is retained in memory with a mirror in `sessionStorage` (`fraudshield_access_token`).
- **Session Restoration**: On page reload, the client invokes `GET /api/v1/auth/me` with the stored token. If the token is invalid or expired (401), session storage is purged and the user is redirected to `/login`.
- **Role Permissions**: Handled strictly via `AuthGuard`. Unauthorized routes trigger `ForbiddenPage` (403) and authenticated navigation routes redirect according to the primary role:
  - `CARDHOLDER` → `/customer`
  - `FRAUD_ANALYST` → `/analyst`
  - `FRAUD_RISK_MANAGER` → `/manager`

## 2. Formatting & Localization
- **Monetary Values**: All monetary amounts received as decimal strings (e.g., `"15000.00"`) are parsed safely and formatted via `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })`. Indian numbering (lakhs / crores) is preserved.
- **Timestamps**: All dates and ISO-8601 timestamps are converted to Indian Standard Time (`Asia/Kolkata`, UTC+05:30) via `dayjs.tz` and rendered with explicit `"IST"` notation (e.g., `23 Oct 2026, 02:30 PM IST`).
- **Card Numbers**: Full Primary Account Numbers (PAN) are NEVER processed, logged, or exposed in the UI. All cards strictly adhere to the `XXXXXXXXXXXX1234` format.

## 3. Real-Time Polling & Invalidation
- **Pending Step-Ups**: Cardholder step-up verification requests are polled every 10 seconds (`refetchInterval: 10000`).
- **Analyst Alert Queue**: Triage queue is refreshed every 5 seconds (`refetchInterval: 5000`) to guarantee alert visibility well within the 5-second NFR target.
- **Manager Dashboard**: Refreshed every 30 seconds when the "Auto-Refresh" toggle is active.
- **Cache Eviction**: Action mutations (card blocks, step-up responses, alert claims, resolutions, and rule updates) invalidate their corresponding query keys to immediately reconcile local state.

## 4. Dual-Control & Maker-Checker Governance
- **Self-Approval Check**: In the High-Value Approvals queue (`/manager/approvals`), if a proposal was authored by the currently logged-in principal (`proposal.proposed_by === user.username`), the UI disables the action button with a tooltip message.
- **Graceful Error Handling**: If the backend rejects a concurrent or prohibited approval with HTTP 403 or 409, the client intercepts the error response and displays a non-blocking snackbar notification.

## 5. Fallback & Degraded States
- **Model Unavailability**: If `model_status === 'MODEL_UNAVAILABLE'` or inference times out, the analyst view presents an assistive banner ("AI score unavailable for this transaction") while keeping all rules engine scores (R1–R13) fully accessible.
- **Untrusted Content Sanitization**: Merchant names, free text, and analyst comments are rendered solely via standard text nodes and never executed as HTML.
