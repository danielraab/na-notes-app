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
- Never bypass parameterized placeholders (`?1`, `?2`, ...) for
  user-controlled input.
- Run `cargo build --all-targets && cargo clippy --all-targets && cargo fmt
  --check && cargo test` before considering a change done.
- **SQLite and PostgreSQL are both first-class engines.** All database
  access goes through the `Backend` trait in `src/db` — see
  [`docs/decisions/0002-database-abstraction-layer.md`](docs/decisions/0002-database-abstraction-layer.md).
  Never add an engine conditional outside `src/db`, and never fork a query
  per engine: write SQL both accept, with `?N` placeholders (`src/db/rebind.rs`
  rewrites them to `$N` for PostgreSQL).
- A database change isn't done until it's been run against **both** engines:
  `cargo test`, then `POSTGRES_TEST_URL=... cargo test` (same suite, real
  server — see the README). CI does both; don't rely on CI to find out.
- Adding a third engine should mean implementing `Backend` and adding an arm
  to `Engine::from_url` — nothing else. If a change would make that untrue,
  that's a signal the abstraction is leaking, not a reason to special-case.
- If a change touches `openapi/openapi.yaml` semantics (new field,
  endpoint, or behavior), update `src/httpapi/dto.rs` and the relevant
  handler together, and flag that other backend implementations need the
  equivalent change.
