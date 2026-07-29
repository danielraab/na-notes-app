# 4. Sessions and in-flight OIDC state stored in SQLite, not memory/Redis

## Status

Accepted

## Context

The backend needs somewhere to keep server-side sessions (ADR 0004 at the
repo root) and short-lived OIDC login state (`state` + PKCE
`code_verifier`, ADR-adjacent detail). Options were an in-memory map, an
external store (Redis), or the existing SQLite database.

## Decision

Store both in SQLite (`sessions` and `oidc_requests` tables, see
`internal/db/migrations/0001_init.sql`), not in memory or a separate
store.

## Consequences

- No second stateful dependency to run/operate — consistent with
  [ADR 0006](../../docs/adr/0006-sqlite-owned-by-backend.md) (only the
  backend has database access; here, its database also holds its own
  session state).
- Sessions survive a backend restart, which an in-memory map wouldn't.
- This does not horizontally scale to multiple backend instances sharing
  one SQLite file for writes — acceptable for this project's goals (one
  backend instance per deployment, used to compare implementations, not a
  production multi-instance service). If that changes, revisit.
- `oidc_requests` rows are opportunistically deleted (expired ones, on
  every new login attempt) rather than run through a background job —
  simplest thing that keeps the table from growing unbounded given how
  rarely logins happen relative to other traffic.
