# 2. SQLite only — no PostgreSQL support

## Status

Accepted. Implements the SQLite half of
[ADR 0013](../../../docs/adr/0013-exchangeable-database-backend.md);
deliberately does not implement the PostgreSQL-opt-in half.

## Context

ADR 0013 makes supporting a second database engine opt-in per backend
implementation — `backend-go` and `backend-rust` both support PostgreSQL
behind their own abstraction layers
(`backend-go/docs/decisions/0005-postgres-support-via-pgx.md`,
`backend-rust/docs/decisions/0002-database-abstraction-layer.md`), but
nothing requires a new implementation to.

## Decision

`backend-python` only supports SQLite, accessed through the standard
library's `sqlite3` module (`app/db.py`) — no ORM, no query builder, no
second engine. `DATABASE_URL`'s scheme is still respected for the forms
ADR 0013 defines: a bare path, `sqlite://<path>`, and `file:<path>` all
open a SQLite file; a `postgres://`/`postgresql://` URL is rejected with a
clear startup error rather than silently falling back to SQLite or
half-supporting Postgres syntax.

A single shared `sqlite3.Connection`, guarded by a lock
(`app/db.py::Database`), plays the same role as `backend-go`'s
`SetMaxOpenConns(1)`: SQLite is single-writer regardless of language, and
one serialized connection is simpler than a pool here given this
project's traffic levels.

## Consequences

- No per-engine query dialect to maintain, no second CI service container,
  no `psycopg`/`asyncpg` dependency.
- A deployment wanting PostgreSQL (multi-writer concurrency, ephemeral
  containers without persistent local storage, comparing engines) should
  use `backend-go` or `backend-rust` instead, or add PostgreSQL support
  here later behind the same kind of abstraction those two use — nothing
  about this decision forecloses that, it just isn't done yet.
- Consistent with ADR 0006/0013: only this backend touches its database,
  migrations (`app/migrations/0001_init.sql`) still run automatically and
  forward-only on startup, and the schema matches
  [`/docs/schema.md`](../../../docs/schema.md) (ADR 0014) exactly, so data
  stays tractable to migrate to/from another implementation.
