# 3. Module layout: repository / service / httpapi

## Status

Accepted. Same rationale as `backend-go/docs/decisions/0003-package-layout.md`,
adapted to Rust modules instead of Go packages.

## Context

The domain has real business rules (permission resolution, optimistic
concurrency, mention-notification de-duplication) that shouldn't be
entangled with HTTP concerns (status codes, cookies, JSON shapes), and
shouldn't require a running HTTP server to unit test.

## Decision

Each domain area (`notes`, `users`, `auth`) is a module with no dependency
on `axum`:

- **`repository.rs`** (`notes`) / the repository methods on `users::Repository`
  — SQL only. Takes/returns domain structs. Returns `apperr::AppError`
  variants (`NotFound`, `Forbidden`, `Validation`, `Internal`), never HTTP
  status codes. The one exception: `notes::repository::UpdateOutcome`
  (`Updated(Note)` / `Conflict(Note)`) carries the optimistic-concurrency
  conflict result directly rather than through an error value, because the
  409 response needs the note's current server copy alongside it — see the
  note in `src/apperr.rs`.
- **`service.rs`** (`notes`) — business rules: authorization checks (who
  can read/edit/delete), the update-conflict → `UpdateResult` mapping,
  mention-diffing and triggering notification emails. This is what
  `src/httpapi` handlers call.
- **`src/httpapi`** — the only module that knows about HTTP: routing
  (`server.rs`), cookies (`cookies.rs`), CORS/CSRF/session middleware
  (`middleware.rs`), request/response mapping (`dto.rs`), and mapping
  `apperr::AppError` to status codes (`respond.rs`).

## Consequences

- `src/notes/repository.rs`'s and `src/users/mod.rs`'s `#[cfg(test)]`
  modules run against a real (temp-file) SQLite database with no HTTP
  server involved, mirroring backend-go's `internal/notes/repository_test.go`.
- Adding a second transport (e.g. a CLI) would only require a new thin
  layer on top of `notes::Service`/`users::Repository`, not a rewrite.
- Same tradeoff backend-go accepted: an extra layer of indirection
  (repository → service → handler) for what are sometimes thin
  pass-throughs, worth it because the permission/concurrency/notification
  logic in `notes::Service` is substantial enough to isolate and test on
  its own.
