# 5. Optional PostgreSQL support via `pgx`, with driver-agnostic repositories

## Status

Accepted. Implements [ADR 0013](../../../docs/adr/0013-exchangeable-database-backend.md)
for this backend.

## Context

`backend-go` defaults to SQLite (`modernc.org/sqlite`, ADR 0002). ADR 0013
allows a backend to opt in to an alternate engine via `DATABASE_URL`
without disturbing that default. `internal/notes`, `internal/users`, and
`internal/auth` all write SQL with SQLite's native `?` placeholders and a
handful of SQLite-flavored constructs; adding PostgreSQL meant deciding
whether to fork queries per engine or keep them shared.

## Decision

- **Driver:** `github.com/jackc/pgx/v5/stdlib`, the `database/sql`-compatible
  shim over `pgx`. Like `modernc.org/sqlite`, it's pure Go, so the Docker
  build stays `CGO_ENABLED=0` with no C toolchain needed. Registered under
  driver name `"pgx"`, opened via `db.OpenPostgres`.
- **Placeholders stay `?` everywhere.** Rather than rewriting every query
  in `internal/notes`/`internal/users`/`internal/auth` (or forking them per
  engine), `internal/db.DB` wraps `*sql.DB` and rewrites `?` to `$1, $2,
  ...` transparently in `ExecContext`/`QueryContext`/`QueryRowContext`
  before handing the query to the underlying driver (`internal/db/rebind.go`).
  SQLite passes through unchanged. This is the same isolation ADR 0002
  established for the driver itself: everything outside `internal/db`
  only ever writes `database/sql` calls and has zero awareness of which
  engine is configured.
- **Migrations run statement-by-statement.** `modernc.org/sqlite` accepts a
  whole migration file (multiple `;`-separated statements) in one `Exec`
  call; PostgreSQL's default (extended) query protocol via `pgx` does not.
  `internal/db.migrate` now splits each migration file into individual
  statements (`splitStatements`, on top-level `;` — not inside a string
  literal) and executes them one at a time inside the same transaction.
  This works identically for SQLite, so there's one code path, not two.
- **One dialect-specific query was made portable instead of forked:**
  `note_mentions`'s insert used SQLite's `INSERT OR IGNORE`; PostgreSQL has
  no equivalent syntax. It's now `INSERT ... ON CONFLICT DO NOTHING`, which
  both SQLite (3.24+) and PostgreSQL support identically — no per-engine
  branching needed.
- **User search was made engine-consistent, not just made-to-work:**
  `internal/users.Repository.Search` used `LIKE`, whose case sensitivity
  differs between SQLite (case-insensitive for ASCII by default) and
  PostgreSQL (case-sensitive). It now wraps both sides in `LOWER(...)`,
  which is portable and keeps prefix search case-insensitive on either
  engine.
- The `0001_init.sql` schema itself needed no changes: plain `TEXT`
  columns, `CHECK` constraints, `REFERENCES ... ON DELETE CASCADE`, and
  `ON CONFLICT (...) DO UPDATE SET x = excluded.x` are all valid,
  identically-behaving syntax on both engines.
- `schema_migrations.applied_at` is now set explicitly by Go code
  (`time.Now().UTC()`) on insert rather than via a SQL-level
  `DEFAULT (datetime('now'))`, since that default expression is
  SQLite-specific.

## Consequences

- Adding a *third* engine means extending `rebind`/`migrate` if its
  placeholder or multi-statement-exec behavior differs from both of these,
  but the repository/store packages themselves shouldn't need to change
  again unless that engine needs SQL this schema doesn't already cover
  portably.
- `sql.DB.SetMaxOpenConns(1)` (needed because SQLite is single-writer,
  ADR 0002) is not applied when using PostgreSQL — it's a real
  client/server database and benefits from a normal connection pool.
- Verified against a live PostgreSQL 16 instance: `internal/db`'s
  integration test (`POSTGRES_TEST_URL=... go test ./internal/db/...`)
  exercises `OpenPostgres`, the migration path, and every
  dialect-sensitive construct above (rebound placeholders, both
  `ON CONFLICT` forms, row-value comparisons, `RowsAffected`) directly
  against it.
- No automatic data migration between SQLite and PostgreSQL — switching
  `DATABASE_URL` on an existing deployment starts from an empty schema on
  the new engine, same as pointing `DATABASE_PATH` at a fresh file.
