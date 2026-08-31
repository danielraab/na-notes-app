# backend-python

Python (FastAPI) implementation of the NA Notes backend. Implements
[`/openapi/openapi.yaml`](../openapi/openapi.yaml) exactly — see the root
[`README.md`](../README.md) and [`/docs/adr`](../docs/adr) for the
cross-cutting rules this implementation follows (auth, CSRF/CORS,
pagination, sharing, concurrency).

## Stack

- Python 3.12, [FastAPI](https://fastapi.tiangolo.com/) on
  [Starlette](https://www.starlette.io/)/[uvicorn](https://www.uvicorn.org/).
- [`uv`](https://docs.astral.sh/uv/) for dependency management (`uv.lock`
  committed) and running the dev tooling.
- The standard library's `sqlite3` — no ORM. This is the only database
  engine supported (see
  [`docs/decisions/0002-sqlite-only.md`](docs/decisions/0002-sqlite-only.md);
  this is a deliberate narrower scope than `backend-go`/`backend-rust`,
  which both also support PostgreSQL per
  [ADR 0013](../docs/adr/0013-exchangeable-database-backend.md)).
- `httpx` + [`joserfc`](https://jose.authlib.org/) for the OIDC
  Authorization Code + PKCE flow and ID token verification — see
  [`docs/decisions/0004-oidc-via-httpx-and-joserfc.md`](docs/decisions/0004-oidc-via-httpx-and-joserfc.md).
- `smtplib` (standard library) for notification emails.

See [`docs/decisions/`](docs/decisions) for the reasoning behind these
choices and the internal package layout.

## Running locally (without Docker)

```bash
cd backend-python
uv sync --group dev
cp .env.example .env   # fill in your OIDC provider + SMTP credentials
mkdir -p data
# set DATABASE_URL=./data/notes.db in .env
set -a
source .env
set +a
uv run python -m app.main
```

The server listens on `LISTEN_ADDR` (default `:8080`). `DATABASE_URL`'s
scheme selects the database file (default a local SQLite file,
`./notes.db`, if unset) — see
[`docs/decisions/0002-sqlite-only.md`](docs/decisions/0002-sqlite-only.md).
Migrations run automatically on startup.

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
app/
  config.py       # environment variable loading
  db.py           # sqlite connection + migrations
  migrations/      # embedded, forward-only SQL migrations
  apperr.py        # sentinel domain errors, mapped to HTTP status in app/api
  randtoken.py     # CSPRNG token generation (sessions, CSRF, share links)
  timeutil.py      # UTC timestamp <-> ISO 8601 string helpers
  mail.py          # SMTP notification emails
  auth/            # OIDC client + session/PKCE-state storage
  users/           # user accounts (created lazily on first login)
  notes/           # notes domain: models, cursor, repository, service (business rules)
  api/             # HTTP routing, middleware, request/response DTOs, error mapping
  app.py           # builds the ASGI app from an already-wired AppState
  main.py          # process entrypoint: env vars -> AppState -> app
```

`app/api` is the only package that imports FastAPI/Starlette; `app/notes`
and `app/users` are plain Python with no web framework dependency, so
they're straightforward to unit test (see `tests/test_notes_repository.py`,
`tests/test_notes_service.py`). `app/app.py` is kept separate from
`app/main.py` so tests can build a full app around an in-memory database
and a fake OIDC client (see `tests/test_api.py`) without any network
access or process environment setup.

## Testing

```bash
uv run pytest
```

`tests/test_notes_repository.py`/`tests/test_users_repository.py` exercise
real SQLite (temp file per test) — sharing visibility, optimistic-concurrency
conflicts, public share tokens, mention tracking, and cursor pagination
correctness under interleaved pages. `tests/test_notes_service.py` covers
the business rules (permission checks, version-conflict handling,
mention-notification de-duplication) with a fake mailer. `tests/test_api.py`
drives the full HTTP stack — login/callback/session/CSRF, note CRUD,
sharing, public notes — through a fake OIDC provider, so it needs no
network access either.

```bash
uv run ruff check .      # lint
uv run ruff format --check .   # formatting
uv run mypy app           # type checking
```

## Docker

```bash
docker build -t na-notes-backend-python .
docker run --rm -p 8080:8080 --env-file .env -v notes-data:/data na-notes-backend-python
```

The image is a multi-stage build: `uv sync --locked` installs dependencies
from the committed lockfile into a virtualenv, which the final stage runs
as a non-root user on `python:3.12-slim-bookworm`. `/healthz` is used for
the container `HEALTHCHECK`.

## Security notes specific to this implementation

- Session IDs, CSRF tokens, OIDC `state`, PKCE `code_verifier`, and public
  share tokens are all generated via `app/randtoken.py` (`secrets.token_urlsafe`,
  a CSPRNG). Never use the `random` module for any of these.
- All SQL always uses parameterized queries (`?` placeholders via
  `sqlite3`) — never string-concatenate user input into SQL.
- The public note endpoint (`GET /api/public/notes/{token}`) intentionally
  omits owner identity and note ID from its response — see
  [ADR 0009](../docs/adr/0009-public-share-random-token.md).
- CSRF comparison uses `hmac.compare_digest` (constant-time) rather than
  `==`, and the double-submit cookie/header names match every other
  backend exactly (ADR 0005).
