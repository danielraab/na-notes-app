# 3. Sessions and in-flight OIDC state stored in SQLite, not memory

## Status

Accepted. Same decision and rationale as
`backend-go/docs/decisions/0004-sessions-in-sqlite.md`.

## Context

The backend needs somewhere to keep server-side sessions (root
[ADR 0004](../../../docs/adr/0004-generic-oidc-httponly-cookie.md)) and
short-lived OIDC login state (`state` + PKCE `code_verifier`). Options were
an in-memory dict or the same SQLite database everything else uses.

## Decision

Store both in SQLite (`sessions` and `oidc_requests` tables, see
`app/migrations/0001_init.sql`), via `app/auth/store.py`, not in memory.

## Consequences

- No second stateful dependency to run/operate — only the backend has
  database access, and here that database also holds its own session
  state.
- Sessions survive a backend restart, which an in-memory dict wouldn't.
- Doesn't horizontally scale to multiple backend instances sharing one
  SQLite file for writes — acceptable for this project's goals (one
  backend instance per deployment, used to compare implementations, not a
  production multi-instance service). If that changes, revisit.
- `oidc_requests` rows are opportunistically deleted (expired ones, on
  every new login attempt, in `AuthStore.create_oidc_request`) rather than
  run through a background job — simplest thing that keeps the table from
  growing unbounded given how rarely logins happen relative to other
  traffic.
