# 1. Plain JDBC over SQLite, no ORM, no PostgreSQL support (yet)

## Status

Accepted.

## Context

ADR 0006 (root) requires each backend to own its SQLite database and run
its own forward-only migrations on startup; ADR 0013 lets a backend
additionally opt in to PostgreSQL via `DATABASE_URL`'s scheme, but makes
that opt-in — a SQLite-only implementation is still conformant.

Quarkus's usual persistence story (`quarkus-hibernate-orm-panache` +
Agroal-managed datasources configured at build time) is built around a
fixed, build-time-known JDBC driver per named datasource. SQLite has no
official Quarkus/Quarkiverse-core datasource extension, and layering an
ORM on top of it would also pull this implementation away from
`backend-go`/`backend-rust`'s style of writing plain parameterized SQL
directly in a thin repository layer.

## Decision

- `app.nanotes.backend.db.Database` hand-rolls what `backend-go`'s
  `internal/db` package does: it owns a single JDBC `Connection` (opened
  via `org.xerial:sqlite-jdbc`, no Agroal pool), runs `PRAGMA
  journal_mode=WAL`, and applies migrations from
  `db/migrations/*.sql` (listed in `db/migrations/index.txt`, tracked in a
  `schema_migrations` table) before the application starts serving
  requests.
- Every statement goes through `Database.query`/`queryOne`/`update`, which
  synchronize on a single lock rather than pooling connections — SQLite is
  single-writer, so this mirrors `backend-go`'s
  `sql.DB.SetMaxOpenConns(1)` rather than fighting it with a connection
  pool that would just serialize on `SQLITE_BUSY` anyway.
- No ORM: `notes`/`users`/`auth` repositories write plain SQL with `?`
  placeholders directly, matching `backend-go`'s and `backend-rust`'s
  style, and keeping the schema (`db/migrations/0001_init.sql`) a direct,
  readable translation of `/docs/schema.md` (ADR 0014).
- **PostgreSQL is not supported by this implementation.** `DATABASE_URL`
  accepts a bare path, `sqlite://<path>`, or `file:<path>`; a
  `postgres://`/`postgresql://` value fails fast at startup with an
  explicit error rather than being silently misinterpreted. This is the
  SQLite-only option ADR 0013 explicitly allows, not a violation of it.
  Revisit if a later contributor wants engine parity with `backend-rust`.

## Consequences

- No Hibernate/Panache dependency, no entity-mapping ceremony — the
  repository layer reads like `backend-go`'s, which keeps the three
  backends easy to compare side by side.
- Because there's exactly one JDBC connection, `Database` is a natural
  bottleneck under heavy concurrent load; acceptable for this project's
  goals (comparing implementations, not a production multi-instance
  service) — same trade-off `backend-go` makes for the same reason.
- A future contributor who wants PostgreSQL support here would need to
  either introduce a driver-dispatch layer like `backend-go`'s
  `internal/db.Open`/`rebind.go`, or a Quarkiverse SQLite datasource
  extension plus Agroal — neither exists yet.
- A pleasant surprise for `docs/decisions/0005-native-image-build.md`:
  `org.xerial:sqlite-jdbc` ships its own GraalVM `Feature` for
  native-image support, so this choice didn't cost anything extra when
  native-image support was added later.
