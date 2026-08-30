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
  image builds `CGO_ENABLED=0` and needs no C toolchain at runtime. This is
  the default database; `github.com/jackc/pgx/v5` (also pure Go) is used
  when `DATABASE_URL` opts into PostgreSQL instead — see
  [ADR 0013](../docs/adr/0013-exchangeable-database-backend.md) and
  [`docs/decisions/0005-postgres-support-via-pgx.md`](docs/decisions/0005-postgres-support-via-pgx.md).
- `github.com/coreos/go-oidc` + `golang.org/x/oauth2` — generic OIDC
  client (authorization code + PKCE).
- `net/smtp` (standard library) for notification emails.

See [`docs/decisions/`](docs/decisions) for the reasoning behind these
choices and the internal package layout.

## Running locally (without Docker)

```bash
cd backend-go
cp .env.example .env   # or export the variables another way
mkdir data
# set DATABASE_URL=./data/notes.db in .env
set -a
source .env
set +a
go run ./cmd/server
```

The server listens on `LISTEN_ADDR` (default `:8080`). `DATABASE_URL`'s
scheme selects the database engine (default a local SQLite file,
`./notes.db`, if unset) — see
[ADR 0013](../docs/adr/0013-exchangeable-database-backend.md):

| `DATABASE_URL` | Engine |
|---|---|
| unset, a bare path (e.g. `./data/notes.db`), `sqlite://<path>`, or `file:<path>` | SQLite file at `<path>` |
| `postgres://...` or `postgresql://...` | PostgreSQL |

Migrations run automatically on startup either way.

## Configuration

All configuration is environment variables — see
[`.env.example`](.env.example) in this folder for the full list. Every
backend implementation in this repo must accept these exact variable
names (see [ADR 0011](../docs/adr/0011-per-implementation-env-files.md)),
so swapping backends via `docker-compose.yml`'s `build.context` doesn't
mean re-deriving config. Required:
`SESSION_SECRET`, `OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`,
`OIDC_CLIENT_SECRET`, `OIDC_REDIRECT_URL`. `COOKIE_DOMAIN` is optional and
only needed when the frontend and backend are deployed on different
subdomains of the same parent domain — without it, the CSRF double-submit
cookie is a frontend-unreadable host-only cookie on the backend's hostname
and every state-changing request fails with `CSRF_REJECTED`.

## Project layout

```
cmd/server/          # entrypoint: wires config, db, services, http server
internal/config/      # environment variable loading
internal/db/          # sqlite/postgres connection + embedded migrations
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

`internal/db` additionally has an opt-in PostgreSQL integration test that
only runs when `POSTGRES_TEST_URL` is set, e.g.:

```bash
docker run --rm -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16
POSTGRES_TEST_URL=postgres://postgres:postgres@localhost:5432/postgres?sslmode=disable go test ./internal/db/...
```

## Docker

```bash
docker build -t na-notes-backend-go .
docker run --rm -p 8080:8080 --env-file .env -v notes-data:/data na-notes-backend-go
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
