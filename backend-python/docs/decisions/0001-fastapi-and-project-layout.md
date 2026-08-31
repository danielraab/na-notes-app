# 1. FastAPI + uv, layered like backend-go

## Status

Accepted

## Context

Python's standard library has no async-capable HTTP server or request
validation layer comparable to Go's `net/http` (used as-is by
`backend-go`), so both a web framework and a dependency/build tool are
third-party choices, the same situation `backend-rust` was in for `axum`
(see `backend-rust/docs/decisions/0001-axum-web-framework.md`).

## Decision

- **FastAPI** (on Starlette/uvicorn) for HTTP: request routing, Pydantic
  request/response validation, and dependency injection for
  auth/CSRF/config access map cleanly onto the same request pipeline shape
  `backend-go` hand-rolls in `internal/httpapi`. Route handlers are
  synchronous `def`s rather than `async def`s — the domain/DB layer
  underneath (`app/db.py`) is synchronous SQLite, and Starlette already
  runs sync endpoints in a thread pool, so there is no async DB driver to
  introduce for this project's traffic levels.
- **uv** for dependency management and the lockfile (`uv.lock`, committed),
  the same role `go.mod`/`go.sum` play for `backend-go` and `Cargo.lock`
  for `backend-rust`.
- **ruff** (lint + format) and **mypy** (type checking) stand in for
  `gofmt`/`go vet`, run in CI the same way.
- Layout mirrors `backend-go`'s package split
  (`backend-go/docs/decisions/0003-package-layout.md`), adapted to Python
  modules:
  - `app/notes/`, `app/users/`, `app/auth/` — domain code (`repository.py`
    for SQL, `service.py` for business rules in `notes`), no FastAPI
    import anywhere in these modules.
  - `app/api/` — the only package that knows about HTTP: routing
    (`routes_*.py`), request/response DTOs (`dto.py`), CORS/CSRF/session
    middleware (`middleware.py`), and mapping domain errors to HTTP status
    codes (`errors.py`).
  - `app/db.py`, `app/mail.py`, `app/randtoken.py`, `app/timeutil.py`,
    `app/apperr.py`, `app/config.py` — the same cross-cutting concerns
    `backend-go` splits into `internal/db`, `internal/mail`,
    `internal/randtoken`, `internal/apperr`, `internal/config`.
- `app/app.py` builds the ASGI app from an already-constructed `AppState`;
  `app/main.py` is the only module that reads process environment
  variables or performs real OIDC discovery. This split exists so tests
  (`tests/test_api.py`) can build a full app around an in-memory database
  and a fake OIDC client with no network access and no environment setup.

## Consequences

- Business rules (permission resolution, optimistic-concurrency conflict
  handling, mention-notification de-duplication) live in
  `app/notes/service.py` and are unit-tested directly (`tests/test_notes_service.py`),
  without an HTTP server, the same way `backend-go`'s `internal/notes/service.go`
  is tested.
- One more explicit layer (repository → service → route) than a minimal
  script would need, accepted for the same reason `backend-go` and
  `backend-rust` accept it: the permission/concurrency/notification logic
  is substantial enough to isolate and test on its own.
- `pyproject.toml` + `uv.lock` is the single source of truth for
  dependencies; the Docker build (`Dockerfile`) installs from the lockfile
  with `uv sync --locked` rather than a floating `pip install -r ...`.
