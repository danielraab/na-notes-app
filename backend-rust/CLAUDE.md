# CLAUDE.md — backend-rust

Read the repo root [`AGENTS.md`](../AGENTS.md) first — it applies here in
full. This file only adds Rust-specific notes.

- Follow standard Rust idiom: `cargo fmt`-clean, `cargo clippy` clean, no
  `unwrap()`/`expect()` in request-handling paths outside cases that are
  genuinely infallible (e.g. formatting a known-valid header value) or a
  poisoned-lock panic that should crash the process (see `src/db/mod.rs`).
- Business rules belong in `src/notes/service.rs` / `src/users/mod.rs`,
  not in `src/httpapi` handlers — see
  [`docs/decisions/0003-module-layout.md`](docs/decisions/0003-module-layout.md).
- Never bypass `rusqlite`'s parameterized placeholders for user-controlled
  input.
- Run `cargo build && cargo clippy --all-targets && cargo fmt --check &&
  cargo test` before considering a change done.
- This implementation only supports SQLite (see
  [`docs/decisions/0002-sqlite-only.md`](docs/decisions/0002-sqlite-only.md))
  — don't add PostgreSQL support without a new decision doc explaining why
  and how (placeholder syntax, connection pooling, migration execution all
  need re-deciding, the way `backend-go/docs/decisions/0005-postgres-support-via-pgx.md`
  did for Go).
- If a change touches `openapi/openapi.yaml` semantics (new field,
  endpoint, or behavior), update `src/httpapi/dto.rs` and the relevant
  handler together, and flag that other backend implementations need the
  equivalent change.
