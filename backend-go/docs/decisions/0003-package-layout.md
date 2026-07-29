# 3. Package layout: repository / service / httpapi

## Status

Accepted

## Context

The domain has real business rules (permission resolution, optimistic
concurrency, mention-notification de-duplication) that shouldn't be
entangled with HTTP concerns (status codes, cookies, JSON shapes), and
shouldn't require an HTTP request in flight to unit test.

## Decision

Each domain area (`notes`, `users`, `auth`) is a plain Go package with no
dependency on `net/http`:

- **`repository.go`** — SQL only. Takes/returns domain structs. Returns
  `internal/apperr` sentinel errors (`ErrNotFound`, `ErrVersionConflict`,
  ...), never HTTP status codes.
- **`service.go`** — business rules: authorization checks (who can read/edit/delete),
  optimistic-concurrency handling, mention-diffing and triggering
  notification emails. This is what `internal/httpapi` handlers call.
- **`internal/httpapi`** — the only package that knows about HTTP: routing,
  cookies, CORS/CSRF, request decoding, and mapping `apperr` sentinels to
  status codes (`respond.go`) and domain structs to the OpenAPI response
  shapes (`dto.go`).

## Consequences

- `internal/notes`'s tests (`repository_test.go`, `cursor_test.go`) run
  against a real (temp-file) SQLite database with no HTTP server, no
  mocking of `net/http`.
- Adding a second transport (e.g. a CLI, or gRPC, hypothetically) would
  only require a new thin layer on top of `internal/notes`/`internal/users`,
  not a rewrite.
- The tradeoff is an extra layer of indirection (repository → service →
  handler) for what are sometimes thin pass-throughs; accepted because the
  permission/concurrency/notification logic in `notes.Service` is
  substantial enough to be worth isolating and testing on its own.
