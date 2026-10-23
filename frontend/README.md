# FraudShield - Frontend Web Application

FraudShield is an enterprise-grade AI-assisted real-time card transaction fraud detection, step-up verification, and alert management platform designed for Kaveri National Bank.

This module houses the complete frontend application built with React 18, TypeScript (strict), Vite, and Material UI v5.

## 🚀 Key Features by Role

### 1. Cardholder Portal (`/customer`)
- **Card Carousel**: Styled physical bank cards displaying masked PAN (`XXXXXXXXXXXX1234`), debit/credit type, daily spend limits, and permanent self-service card locking.
- **Transactions DataGrid**: Comprehensive log with IST timestamps, merchant names, channels, INR amounts, and neutral status descriptions.
- **Step-Up Verifications**: Prominent verification cards with live countdown timers, OTP challenge inputs, and permanent card-lock confirmation for unrecognized charges (`NOT_ME`).
- **Spend Visualizations**: Donut charts for channel distribution, spend category breakdowns, and monthly trends using `@mui/x-charts`.

### 2. Fraud Analyst Portal (`/analyst`)
- **Alert Queue**: Real-time queue polling every 5 seconds, sorted by `HIGH` priority first then ascending SLA deadline. Visually highlights overdue `SLA_BREACHED` alerts.
- **Alert Detail Investigation**:
  - Full evidence dossier: transaction fields, derived telemetry features (`velocity_10m`, `speed_kmph`, `new_merchant_flag`), and triggered rules (R1–R13).
  - Rule Risk Score Gauge (0–100).
  - AI Explainability Panel: XGBoost normalized fraud probability, SHAP contributing factors, and model versioning.
  - Card Historical Activity: Sequence line chart and prior authorization grid.
  - Claim & Resolve: Enforces minimum 10-character notes and automatically triggers manager approval (`PENDING_MANAGER_APPROVAL`) for false-positive resolutions strictly greater than ₹100,000.

### 3. Fraud Risk Manager Portal (`/manager`)
- **Executive Dashboard**: Portfolio-level KPI strip (authorizations, block rate, step-up rate, open alerts, SLA breaches, false positive rate) with stacked decision trends and reason code distributions.
- **High-Value Approvals Queue**: Dual-control maker-checker governance for high-value false-positive proposals. Automatically prevents self-approval.
- **Immutable Audit Trail**: Chronological event timeline supporting entity searches across transactions, alerts, cards, and configuration versions, with one-click CSV export.
- **Dynamic Rule Configuration**: Interface for tuning velocity thresholds, amount spikes, impossible travel speed, odd-hour ceilings, high-risk MCC chips, and AI probability boundaries with optimistic concurrency.

---

## 🛠️ Technology Stack

- **Framework**: React 18 + TypeScript (strict mode) + Vite
- **UI System**: Material UI v5 (`@mui/material`, `@mui/icons-material`, `@emotion/react`, `@emotion/styled`)
- **Data & Charts**: `@mui/x-data-grid`, `@mui/x-charts`, `@mui/x-date-pickers` + `dayjs`
- **State & Networking**: `@tanstack/react-query` v5, Axios, React Router v6
- **Forms & Validation**: React Hook Form + Zod resolvers
- **Testing & Mocking**: Vitest, `@testing-library/react`, Mock Service Worker (MSW v2)

---

## 💻 Getting Started

### Prerequisites
- Node.js 18+ (tested on Node.js 24)
- npm 9+

### Installation
```bash
cd frontend
npm install
```

### Environment Configuration
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Key variables:
- `VITE_API_BASE_URL`: Base URL for Spring Boot backend (default `/api`)
- `VITE_USE_MOCKS`: Set to `true` to enable standalone MSW browser worker (default `true` for frontend dev)
- `VITE_ENABLE_DEMO_TOOLS`: Enables jury/evaluator demo tools in Manager portal

### Running Development Server
```bash
npm run dev
```
The app will be accessible at `http://localhost:3000`.

### Running Tests & Quality Checks
```bash
# Run unit & integration tests
npm test -- --run

# Run TypeScript type check
npm run typecheck

# Run production build
npm run build
```

---

## 🔐 Demo Credentials (MSW Mock Mode)

When `VITE_USE_MOCKS=true`, the following credentials can be used:

| Role | Username | Password | Default Redirect |
|---|---|---|---|
| Cardholder | `cardholder1` | any password | `/customer` |
| Fraud Analyst | `analyst1` | any password | `/analyst/alerts` |
| Fraud Risk Manager | `manager1` | any password | `/manager` |
