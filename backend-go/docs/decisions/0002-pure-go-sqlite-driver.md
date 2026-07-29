# 2. modernc.org/sqlite (pure Go) instead of mattn/go-sqlite3 (cgo)

## Status

Accepted

## Context

The most common Go SQLite driver, `mattn/go-sqlite3`, wraps the C SQLite
library via cgo. That requires a C toolchain and glibc/musl compatibility
at both build and (for dynamic linking) run time, which complicates
cross-compilation and Docker multi-stage builds, and disables some Go
toolchain features (e.g. straightforward static binaries, some race
detector interactions).

## Decision

Use `modernc.org/sqlite`, a transpiled pure-Go SQLite implementation. It
implements `database/sql/driver`, so the rest of the codebase (`internal/db`,
`internal/notes`, `internal/users`, `internal/auth`) only ever touches
`database/sql` and has zero awareness of which driver is in use.

## Consequences

- The Docker build uses `CGO_ENABLED=0`, producing a fully static binary
  and letting the final image be minimal (Alpine, no C runtime needed).
- Slightly slower than the cgo driver for heavy write workloads; not a
  concern at this project's scale (single-writer SQLite behind a REST API
  used for testing/comparison, not a high-throughput production system).
- `sql.DB.SetMaxOpenConns(1)` is set in `internal/db/db.go` regardless of
  driver, since SQLite is single-writer — this decision doesn't change
  that constraint.
