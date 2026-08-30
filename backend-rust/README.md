# backend-rust

Rust implementation of the NA Notes backend. Implements
[`/openapi/openapi.yaml`](../openapi/openapi.yaml) exactly — see the root
[`README.md`](../README.md) and [`/docs/adr`](../docs/adr) for the
cross-cutting rules this implementation follows (auth, CSRF/CORS,
pagination, sharing, concurrency).

## Stack

- Rust (2021 edition), [`axum`](https://github.com/tokio-rs/axum) +
  [`tokio`](https://tokio.rs) for HTTP.
- `rusqlite` (bundled SQLite) — the only supported database engine; see
  [ADR 0013](../docs/adr/0013-exchangeable-database-backend.md) and
  [`docs/decisions/0002-sqlite-only.md`](docs/decisions/0002-sqlite-only.md)
  for why this implementation doesn't also offer PostgreSQL.
- [`openidconnect`](https://docs.rs/openidconnect) — generic OIDC client
  (authorization code + PKCE).
- [`lettre`](https://lettre.rs) for notification emails over SMTP.

See [`docs/decisions/`](docs/decisions) for the reasoning behind these
choices and the internal module layout.

## Running locally (without Docker)

```bash
cd backend-rust
cp .env.example .env   # or export the variables another way
mkdir -p data
# set DATABASE_URL=./data/notes.db in .env
set -a
source .env
set +a
cargo run
```

The server listens on `LISTEN_ADDR` (default `:8080`). `DATABASE_URL`
selects the SQLite file to open — a bare path (e.g. `./data/notes.db`),
`sqlite://<path>`, or `file:<path>` (default `./notes.db` if unset).
Migrations run automatically on startup.

## Configuration

All configuration is environment variables — see
[`.env.example`](.env.example) in this folder for the full list. Every
backend implementation in this repository must accept these exact variable
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
src/main.rs        # entrypoint: wires config, db, services, http server
src/config.rs       # environment variable loading
src/db/             # SQLite connection + embedded migrations
src/auth/           # OIDC client + session/PKCE-state storage
src/users/          # user accounts (created lazily on first login)
src/notes/          # notes domain: model, cursor, repository, service (business rules)
src/mail/           # SMTP notification emails
src/httpapi/         # HTTP routing, middleware, request/response mapping
src/apperr.rs        # sentinel domain errors, mapped to HTTP status in httpapi
src/randtoken.rs      # CSPRNG token generation (sessions, CSRF, share links)
```

`src/httpapi` is the only module that knows about HTTP; `src/notes` and
`src/users` don't depend on `axum`, so they're straightforward to unit
test (see the `#[cfg(test)]` modules in `src/notes/repository.rs`,
`src/notes/cursor.rs`, and `src/users/mod.rs`).

## Testing

```bash
cargo test
```

`src/notes/repository.rs` has repository-level tests that exercise real
SQLite (temp file per test) — sharing visibility, optimistic-concurrency
conflicts, public share tokens, mention tracking, and cursor pagination
correctness under interleaved pages. `src/users/mod.rs` and
`src/notes/cursor.rs` have their own focused unit tests.

## Docker

```bash
docker build -t na-notes-backend-rust .
docker run --rm -p 8080:8080 --env-file .env -v notes-data:/data na-notes-backend-rust
```

The image is a multi-stage build: compiles a release binary (`rusqlite`'s
bundled SQLite needs a C toolchain in the build stage only — see
[`docs/decisions/0002-sqlite-only.md`](docs/decisions/0002-sqlite-only.md)),
then runs it as a non-root user on a minimal Debian base. `/healthz` is
used for the container `HEALTHCHECK`.

## Security notes specific to this implementation

- Session IDs, CSRF tokens, OIDC `state`, PKCE `code_verifier`, and public
  share tokens are all generated via `src/randtoken.rs`
  (`rand::rngs::OsRng`, the OS CSPRNG). Never use a non-cryptographic RNG
  for these.
- Every SQL statement uses `rusqlite`'s parameterized placeholders (`?1`,
  `?2`, ...) — never string-concatenate user input into SQL.
- CSRF token comparison is constant-time (`src/httpapi/middleware.rs`),
  mirroring Go's `crypto/subtle.ConstantTimeCompare`.
- The public note endpoint (`GET /api/public/notes/{token}`) intentionally
  omits owner identity and note ID from its response — see
  [ADR 0009](../docs/adr/0009-public-share-random-token.md).
