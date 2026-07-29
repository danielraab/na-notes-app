# 6. SQLite, owned exclusively by the backend

## Status

Accepted

## Context

Only the backend may access the database; the frontend only ever talks
REST over HTTPS to the backend. The project favors low operational
overhead for a testbed of many implementations, so a full client/server
database is unnecessary.

## Decision

- Each backend implementation uses **SQLite** as its database, stored as a
  file inside a Docker volume (path configurable via `DATABASE_PATH`,
  default `/data/notes.db`).
- Each backend owns its own schema and migrations; schemas are not shared
  across implementations (they're free to model the same concepts
  differently internally, e.g. different indexing), but must produce the
  same API-level behavior.
- Migrations run automatically on backend startup (idempotent, forward-only).
- No implementation exposes direct DB access to the frontend or to other
  backends. All access goes through the REST API.

## Consequences

- No implementation needs to run/operate a separate database server.
- Switching backend implementations means switching data files; there is
  no shared data between e.g. `backend-go` and a future `backend-rust` —
  each is a self-contained deployment.
- Because SQLite is single-writer, backends should use WAL mode and keep
  transactions short; this is an implementation detail documented per
  backend.
