# 2. Both engines behind one `Backend` trait

## Status

Accepted. Implements [ADR 0013](../../../docs/adr/0013-exchangeable-database-backend.md)
for this backend.

## Context

ADR 0013 makes `DATABASE_URL`'s scheme select the database engine, and lets
an implementation choose how many engines it supports. This backend supports
**both SQLite and PostgreSQL as first-class engines** — neither is a
bolted-on extra, and a change must keep working on both.

The risk with two engines is that engine-awareness leaks upward: repositories
sprouting `if postgres { ... } else { ... }`, forked per-engine queries, or
subtly different behavior that only shows up on the engine you don't run
locally. `backend-go` avoids that by having every repository write one set of
SQL against `database/sql` and rewriting placeholders inside `internal/db`
(see `backend-go/docs/decisions/0005-postgres-support-via-pgx.md`). Rust has
no `database/sql`: `rusqlite` and `tokio-postgres` share no common interface,
have different row/parameter types, and differ on sync-vs-async.

## Decision

Define the interface ourselves. `src/db` exposes a `Backend` trait with four
methods — `execute`, `query_opt`, `query_all`, `run_migration` — and `Db`, the
handle every repository holds, is a thin wrapper over an `Arc<dyn Backend>`.
Engine selection happens exactly once, in `Db::open`, dispatching on the
`DATABASE_URL` scheme.

Everything above `src/db` is engine-agnostic by construction:

- **One canonical SQL dialect.** Repositories write SQLite's numbered `?N`
  placeholders and syntax that both engines accept identically (`ON CONFLICT
  ... DO UPDATE`, `ON CONFLICT DO NOTHING`, row-value comparison, `LOWER(...)
  LIKE`). The PostgreSQL backend rewrites `?N` to `$N` in
  `src/db/rebind.rs`; SQLite passes through untouched. Because both forms are
  *numbered*, this is a 1:1 textual mapping, so a query may reference one
  parameter twice (as `users` search does with `?2`) and still rebind
  correctly — something backend-go's counting rewrite of unnumbered `?`
  placeholders cannot express.
- **One value type.** `db::Value` (`Null`/`Text`/`Int`) covers every bind
  parameter and every column this schema uses, and `db::Row` reads columns
  positionally (`text`, `opt_text`, `int`). Each backend converts to and
  from its own driver's types — including PostgreSQL's stricter typing, where
  an `Int` narrows to `int2`/`int4`/`int8` depending on what the server asks
  for (`version` is `INTEGER`, but `COUNT`/`LIMIT` are `bigint`).
- **One migration runner.** Listing embedded migrations, ordering them and
  checking `schema_migrations` is shared code in `src/db/mod.rs`; only the
  per-file transaction is delegated, since that is the one place the group of
  statements has to be all-or-nothing.

Adding a third engine means implementing `Backend` and adding one arm to
`Engine::from_url`. No repository changes.

## Consequences

- **The test suite is the conformance suite.** `TestDb` (`src/db/testsupport.rs`)
  runs the *same* repository/session tests against SQLite by default, or
  against a real PostgreSQL server when `POSTGRES_TEST_URL` is set — so both
  implementations of the trait are held to identical behavior instead of
  PostgreSQL only being exercised by a separate, thinner test. CI runs the
  suite both ways.
- Writing SQL both engines accept is a real constraint on future changes. It
  has not cost anything so far (the schema is plain `TEXT`/`INTEGER` columns),
  but a future feature wanting an engine-specific feature has to either find
  the portable spelling or extend `Backend` deliberately.
- `Value` is intentionally not extensible-by-accident: adding a type (say
  blobs, or real timestamps) means adding a variant and handling it in both
  backends, which is exactly the review conversation that change deserves.
- SQLite still serializes through one connection behind a mutex, on a
  blocking thread (`rusqlite` is synchronous, and SQLite is single-writer —
  the same reason backend-go sets `SetMaxOpenConns(1)`). PostgreSQL uses a
  normal `deadpool` connection pool.
- PostgreSQL TLS is configured with an explicitly named rustls crypto
  provider rather than the process default: both `ring` and `aws-lc-rs` reach
  this binary through reqwest/lettre, which leaves rustls with no unambiguous
  default and panics at first use. Verified against a live PostgreSQL 16.
- `Db::open` creates a SQLite file if it's missing, but never creates a
  PostgreSQL database — the DSN must point at one that exists.
