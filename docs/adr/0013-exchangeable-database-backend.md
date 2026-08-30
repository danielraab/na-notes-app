# 13. Exchangeable database backend via `DATABASE_URL`

## Status

Accepted. Amends [ADR 0006](0006-sqlite-owned-by-backend.md): "each backend
uses SQLite, configured via `DATABASE_PATH`" becomes "each backend defaults
to SQLite and may opt in to supporting an alternate engine, both configured
through a single `DATABASE_URL` variable." Everything else in ADR 0006
(only the backend touches the database, migrations run automatically and
forward-only, no data is shared across implementations or engines) still
holds.

## Context

ADR 0006 fixed SQLite as *the* database for every backend implementation,
favoring zero operational overhead for a testbed comparing languages and
frameworks. That's still the right default. But it's also useful to be
able to point a given backend at a real client/server database (starting
with PostgreSQL) — e.g. to try it under multi-writer concurrency, to
deploy it somewhere SQLite's single-writer model or local-file storage is
inconvenient (ephemeral containers, multiple replicas), or simply to
compare the same implementation against a different engine.

## Decision

- A backend implementation may support connecting to an alternate database
  engine through a single `DATABASE_URL` environment variable, whose
  **scheme selects the engine**:
  - A bare path (e.g. `./notes.db`, `/data/notes.db`), `sqlite://<path>`,
    or `file:<path>` all open a SQLite file at `<path>`.
  - `postgres://user:pass@host:5432/dbname` (or `postgresql://...`)
    connects to PostgreSQL.
  - If unset, the implementation falls back to a sensible default SQLite
    path (`backend-go` uses `./notes.db`).
  - This replaces the SQLite-only `DATABASE_PATH` variable ADR 0006
    introduced — `backend-go` no longer reads `DATABASE_PATH`.
- Supporting an alternate engine at all is opt-in per implementation, the
  same way choosing an ORM/driver is (ADR 0002's polyglot premise:
  implementations don't have to converge on internals). An implementation
  that only ever supports SQLite is still conformant; it just doesn't
  parse a scheme out of `DATABASE_URL`, or ignores anything but the SQLite
  forms.
- The ownership invariants from ADR 0006 don't change: the frontend still
  never touches the database, migrations still run automatically and
  forward-only on startup, and each backend still owns its own schema.
  Switching engines is data-file-swap territory just like switching
  backend implementations is — there is no automatic migration of data
  between SQLite and PostgreSQL (or between engines in general).
- The variable name `DATABASE_URL` (like every other env var name) is part
  of the cross-implementation contract (ADR 0011): any backend implements
  it under this exact name, so swapping `docker-compose.yml`'s
  `build.context` still needs no other change for consumers that don't
  care which engine is in use.

## Consequences

- An implementation that supports more than one engine has to keep its SQL
  reasonably portable (or maintain per-engine variants) and account for
  placeholder-syntax / dialect differences between engines. That's an
  implementation detail, documented in that implementation's own
  `docs/decisions/` (see `backend-go/docs/decisions/0005-postgres-support-via-pgx.md`
  for how `backend-go` does it).
- One variable to set instead of two (no more picking between
  `DATABASE_PATH` and `DATABASE_URL`, or reasoning about which one wins).
- Because engine choice is opt-in and per-implementation, don't assume
  every backend supports PostgreSQL (or any other engine) just because
  `backend-go` does — check that implementation's own docs.
