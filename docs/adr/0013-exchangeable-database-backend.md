# 13. Exchangeable database backend via `DATABASE_URL`

## Status

Accepted. Amends [ADR 0006](0006-sqlite-owned-by-backend.md): "each backend
uses SQLite" becomes "each backend defaults to SQLite, and may opt in to
supporting an alternate engine at deploy time." Everything else in ADR
0006 (only the backend touches the database, migrations run automatically
and forward-only, no data is shared across implementations or engines)
still holds.

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
  engine via an optional `DATABASE_URL` environment variable (e.g.
  `postgres://user:pass@host:5432/dbname`). When unset, the implementation
  keeps its existing zero-config default (SQLite at `DATABASE_PATH` for
  `backend-go`).
- `DATABASE_URL`, when set, takes priority over `DATABASE_PATH`.
- Supporting this is opt-in per implementation, the same way choosing an
  ORM/driver is (ADR 0002's polyglot premise: implementations don't have
  to converge on internals). An implementation that only ever supports
  SQLite is still conformant; it simply doesn't read `DATABASE_URL`.
- The ownership invariants from ADR 0006 don't change: the frontend still
  never touches the database, migrations still run automatically and
  forward-only on startup, and each backend still owns its own schema.
  Switching engines is data-file-swap territory just like switching
  backend implementations is — there is no automatic migration of data
  between SQLite and PostgreSQL (or between engines in general).
- The variable name `DATABASE_URL` (like every other env var name) is part
  of the cross-implementation contract (ADR 0011): any implementation that
  chooses to support an alternate engine must read it under this exact
  name, so swapping `docker-compose.yml`'s `build.context` still needs no
  other change for consumers that don't care which engine is in use.

## Consequences

- An implementation that supports more than one engine has to keep its SQL
  reasonably portable (or maintain per-engine variants) and account for
  placeholder-syntax / dialect differences between engines. That's an
  implementation detail, documented in that implementation's own
  `docs/decisions/` (see `backend-go/docs/decisions/0005-postgres-support-via-pgx.md`
  for how `backend-go` does it).
- Nothing changes for anyone not setting `DATABASE_URL`: existing
  deployments keep using SQLite exactly as before.
- Because engine choice is opt-in and per-implementation, don't assume
  every backend supports PostgreSQL (or any other engine) just because
  `backend-go` does — check that implementation's own docs.
