# backend-go

Go implementation of the NA Notes backend. Implements
[`/openapi/openapi.yaml`](../openapi/openapi.yaml) exactly — see the root
[`README.md`](../README.md) and [`/docs/adr`](../docs/adr) for the
cross-cutting rules this implementation follows (auth, CSRF/CORS,
pagination, sharing, concurrency).

## Stack

- Go 1.25, standard library `net/http` (method+path routing, no router
  dependency needed).
- `modernc.org/sqlite` — pure-Go SQLite driver (no CGO), so the Docker
  image builds `CGO_ENABLED=0` and needs no C toolchain at runtime.
- `github.com/coreos/go-oidc` + `golang.org/x/oauth2` — generic OIDC
  client (authorization code + PKCE).
- `net/smtp` (standard library) for notification emails.

See [`docs/decisions/`](docs/decisions) for the reasoning behind these
choices and the internal package layout.

## Running locally (without Docker)

```bash
cd backend-go
cp ../.env.example .env   # or export the variables another way
export $(grep -v '^#' .env | xargs)
go run ./cmd/server
```

The server listens on `LISTEN_ADDR` (default `:8080`) and creates its
SQLite file at `DATABASE_PATH` (default `./notes.db`), running migrations
automatically on startup.

## Configuration

All configuration is environment variables — see the root
[`.env.example`](../.env.example) for the full, shared list. Required:
`SESSION_SECRET`, `OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`,
`OIDC_CLIENT_SECRET`, `OIDC_REDIRECT_URL`.

## Project layout

```
cmd/server/          # entrypoint: wires config, db, services, http server
internal/config/      # environment variable loading
internal/db/          # sqlite connection + embedded migrations
internal/auth/         # OIDC client + session/PKCE-state storage
internal/users/        # user accounts (created lazily on first login)
internal/notes/        # notes domain: model, repository, service (business rules)
internal/mail/          # SMTP notification emails
internal/httpapi/       # HTTP routing, middleware, request/response mapping
internal/apperr/        # sentinel domain errors, mapped to HTTP status in httpapi
internal/randtoken/     # CSPRNG token generation (sessions, CSRF, share links)
```

`internal/httpapi` is the only package that knows about HTTP; `internal/notes`
and `internal/users` are plain Go with no framework dependency, so they're
straightforward to unit test (see `internal/notes/*_test.go`).

## Testing

```bash
go test ./...
```

`internal/notes` has repository-level tests that exercise real SQLite
(temp file per test) — sharing visibility, optimistic-concurrency
conflicts, public share tokens, mention tracking, and cursor pagination
correctness under interleaved pages.

## Docker

```bash
docker build -t na-notes-backend-go .
docker run --rm -p 8080:8080 --env-file ../.env -v notes-data:/data na-notes-backend-go
```

The image is a multi-stage build: compiles a static (`CGO_ENABLED=0`)
binary, then runs it as a non-root user on Alpine. `/healthz` is used for
the container `HEALTHCHECK`.

## Security notes specific to this implementation

- Session IDs, CSRF tokens, OIDC `state`, PKCE `code_verifier`, and public
  share tokens are all generated via `internal/randtoken` (`crypto/rand`).
  Never use `math/rand` for any of these.
- `SELECT`/`INSERT`/`UPDATE` statements always use parameterized queries
  (`?` placeholders) — never string-concatenate user input into SQL.
- The public note endpoint (`GET /api/public/notes/{token}`) intentionally
  omits owner identity and note ID from its response — see
  [ADR 0009](../docs/adr/0009-public-share-random-token.md).
