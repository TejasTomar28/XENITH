# FraudShield module ownership and collaboration

This document defines edit boundaries for three engineers and their Codex agents. It is a project coordination policy, not a business requirement from the HCLTech case study. Read [../AGENTS.md](../AGENTS.md) and [api-contracts.md](api-contracts.md) before work. No developer names or GitHub handles have been assigned to modules yet.

## Allowed paths and responsibilities

| Assigned agent | Exclusive application edit scope | Responsibilities |
| --- | --- | --- |
| Backend | `backend/**` | Java 17, Spring Boot, Maven; JWT/RBAC and gateway key; authorization validation/idempotency; R1–R13/history; orchestration; step-up; alerts; maker-checker; audit; JPA/PostgreSQL; module tests |
| Frontend | `frontend/**` | React/Vite/Tailwind; Cardholder, Analyst and Manager screens; simulator; reusable feature components; REST integration and UI tests |
| AI | `ai-service/**` | Python 3.11+/FastAPI schemas, model training/loading/inference, probability and three explanations, evaluation/model card and module tests; no independent writes to application DB |

Role/module assignment does not authorize modification of any other module. PostgreSQL is owned through backend persistence. React calls Spring Boot; only Spring Boot calls internal FastAPI. AI handles inference failures explicitly; backend enforces the 150 ms model budget and rules fallback.

## Protected shared paths

`AGENTS.md`, `README.md`, `docker-compose.yml`, `.env.example`, `.gitignore`, `docs/**`, `data/**`, `output/**`, `scripts/**`, `.github/workflows/**` and all root configuration are protected. An agent needs explicit user authorization for the exact shared files and task. This documentation task authorizes only `AGENTS.md`, `docs/api-contracts.md` and `docs/module-ownership.md`; it does not authorize application work, other file edits, a commit or a push.

The README and initial architecture file still name Java 21. The current project requires Java 17. Report the discrepancy and request a separately scoped correction; do not change those files as a side effect of module work.

## Before editing

1. Identify the assigned module. Ask if application work has no module assignment.
2. Inspect `git status --short --branch`, existing unstaged/staged diffs and repository instructions. Preserve other developers' changes.
3. Read the shared API contracts; identify unsettled decisions and acceptance criteria.
4. List intended file edits and relevant module tests. Avoid commands that write outside that scope, including shared-output generation.

## Cross-module requests

Implement only your side of an existing contract. When integration requires another path, document the required change, affected endpoint/schema, expected behavior, acceptance test and owner. Stop at that boundary. Continue independent authorized work if possible. Never fix another module opportunistically or silently alter a shared contract. Proposed endpoint names and unresolved design choices must not be described as official HCLTech specifications.

No broad formatting, destructive Git operations, deletion/reset/revert of others' changes, automatic branch integration or blanket staging. If unrelated work is staged, report it before a commit rather than including or silently unstaging it.

## Independent branches and checkouts

Use separate clones or worktrees when several Codex agents run on one Mac. Worktrees provide separate working files and indexes but share Git objects and refs; coordinate branch names and never run two agents against one checkout. A shared working directory does not provide isolation.

Suggested branches: `feature/backend-core`, `feature/frontend-dashboard`, `feature/ai-fraud-model`. Create/use the branch assigned by the user. Inspect clean status, fetch origin and fast-forward main from origin/main before starting a new branch. Do not pull across uncommitted changes. Existing feature-branch integration with main needs explicit merge/rebase authorization; report divergence instead of choosing a strategy silently. Follow host/repository worktree tooling when creating an authorized worktree. Worktrees do not copy uncommitted changes automatically.

## Test review commit and PR

1. Modify only authorized paths and run module-specific checks covering critical requirements.
2. Review diff and status, including generated artifacts and secrets; do not stage data/credentials/models by accident.
3. Report test commands, results and limitations. Never describe an unexecuted check as passing.
4. Commit only on explicit request. Inspect every staged path and its diff; use explicit file paths. Shared-file exceptions must be named in the user's authorization.
5. Push feature branches and open a scoped pull request when requested; describe behavior, contracts and validation.
6. Merge only with explicit permission after review and integration checks. Do not merge, rebase, force-push or push to main otherwise.

Meaningful checks include backend rule boundaries/precedence, authorization isolation, idempotency, concurrent claim/history, step-up races and maker-checker; frontend role views/API error states and neutral cardholder responses; AI schema/output validation, deadline-related failure behavior and reproducible out-of-time evaluation. Full sample parity cannot be claimed until the official data pack is available.

## Required completion handoff

Every agent reports its module, changed files, implemented functionality or documentation scope, checks/results, limitations, required changes owned elsewhere and a suggested commit message. Include any unresolved contract decisions and state commit/push status. Explicit shared-file permissions expire with their task and do not transfer ownership to a module agent.
