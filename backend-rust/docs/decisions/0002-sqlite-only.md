# 2. SQLite only — no PostgreSQL support

## Status

Accepted. A deliberate subset of [ADR 0013](../../../docs/adr/0013-exchangeable-database-backend.md),
not a deviation from it: ADR 0013 explicitly says "An implementation that
only ever supports SQLite is still conformant."

## Context

`backend-go` defaults to SQLite and optionally supports PostgreSQL via
`DATABASE_URL`'s scheme (see `backend-go/docs/decisions/0005-postgres-support-via-pgx.md`),
by rewriting its own `?`-placeholder SQL to PostgreSQL's `$1, $2, ...` at
the `database/sql` layer. Rust has no equivalent "one API, either driver"
story as cheap as Go's: `database/sql` is a stdlib interface every driver
implements uniformly, whereas Rust's two natural choices for this
(`sqlx`'s `Any` driver, or hand-writing a dispatch-by-engine wrapper like
`backend-go/internal/db.DB`) both mean meaningfully more code than
backend-go's single `rebind()` function — either a heavier dependency and
its own placeholder-translation behavior to trust, or reimplementing
row/column mapping generically over two different driver row types by
hand.

## Decision

Support only SQLite, via `rusqlite` (with the `bundled` feature — it
vendors and compiles SQLite's C sources, so no system SQLite package is
required at build or run time). `DATABASE_URL` still accepts the SQLite
forms ADR 0013 defines — a bare path, `sqlite://<path>`, or `file:<path>`
— for compatibility with the shared env var contract; a `postgres://` or
`postgresql://` value is rejected at startup with a clear error rather
than silently falling back to SQLite.

Since there's exactly one engine, there's no placeholder-rewriting layer
like `backend-go/internal/db/rebind.go` — every query is written directly
against `rusqlite`'s native `?1, ?2, ...` placeholders.

## Consequences

- Simpler `src/db` module: one `Connection`, no driver dispatch, no
  per-engine SQL portability constraints (backend-go's
  `docs/decisions/0005-postgres-support-via-pgx.md` documents several —
  `LIKE` case-sensitivity, `INSERT OR IGNORE` vs `ON CONFLICT DO NOTHING`,
  statement-by-statement migration execution — none of which apply here).
- A single `rusqlite::Connection` behind a `Mutex`, called through
  `tokio::task::spawn_blocking` (see `src/db/mod.rs`), mirrors backend-go's
  own `SetMaxOpenConns(1)` restriction for the same reason: SQLite is
  single-writer, so there's nothing to gain from pooling connections
  in-process.
- If this implementation later needs PostgreSQL (e.g. to compare against
  it, as `backend-go` does), that's a new decision to make then — most
  likely `sqlx` with its `Any` driver, or a hand-rolled dispatch layer akin
  to backend-go's — not a reason to avoid documenting today's simpler
  choice.
- The Docker build needs a C toolchain in its build stage (for
  `rusqlite`'s bundled SQLite), unlike backend-go's `CGO_ENABLED=0`
  pure-Go driver — but nothing in the final runtime image, which stays a
  small non-root Debian base. See `Dockerfile`.
