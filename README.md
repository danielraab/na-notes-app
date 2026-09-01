# NA Notes

A notes application implemented multiple times, in different backend and
frontend languages/frameworks, to compare them under an identical
product spec. Any backend can be paired with any frontend: they only ever
talk to each other over the [shared REST contract](openapi/openapi.yaml).

## Why this repo is structured this way

This is a testbed for programming languages and frameworks, not a single
product with one implementation. The rules that make that possible live in
[`docs/adr/`](docs/adr) — read those before adding a new implementation.
The short version:

- `backend-<tech>/` — one backend implementation (e.g. `backend-go`).
  Owns its own SQLite database; the frontend never touches the database
  directly. Implements the OpenAPI contract exactly.
- `frontend-<tech>/` — one frontend implementation (e.g. `frontend-react`,
  `frontend-svelte`, `frontend-vue`, `frontend-angular`). Talks to
  whichever backend is configured, only via the REST API.
- `openapi/openapi.yaml` — the single API contract every backend
  implements and every frontend consumes. Lives outside every
  implementation folder on purpose (ADR 0003).
- `docs/adr/` — cross-cutting architecture decisions that apply to every
  implementation (auth, pagination, concurrency, sharing, etc).
- `docs/schema.md` — a non-binding reference data model, so implementations
  stay close enough to make migrating data between them tractable
  (ADR 0014). Not a tested contract — each backend still owns its own
  actual schema (ADR 0006).
- Each implementation folder has its own `README.md` (how to run it) and
  `docs/decisions/` (choices specific to that implementation, e.g. "why
  this Go router").

## Feature set (identical across every implementation)

- Login via a generic OIDC provider (any standards-compliant provider);
  session lives in an HttpOnly cookie, never touched by frontend JS.
- CORS locked to the configured frontend origin; CSRF protected via
  double-submit cookie.
- Only the backend has database access (SQLite, one file per backend).
- Homepage is a dashboard of the user's notes: 12 initially, more loaded
  on scroll via cursor pagination. Logged-out visitors see one sample note
  and a login button.
- Notes are always editable as Markdown (rich text is optional, must
  serialize to/from Markdown).
- Notes can be shared with specific users (read-only or editable) or
  published publicly (always read-only, via an unguessable random-token
  link — see [ADR 0009](docs/adr/0009-public-share-random-token.md)).
- Users can be @-mentioned in a note.
- Mentioning a user or sharing a note with them sends an email (via SMTP).
- Every backend and every frontend builds its own Docker image. CI builds
  only the implementations whose folder changed
  ([ADR 0015](docs/adr/0015-ci-runs-only-changed-implementations.md)) and,
  on push to `main`, publishes each to
  `ghcr.io/<owner>/na-notes-<impl>:YYYYMMDD-N`
  ([ADR 0016](docs/adr/0016-ci-publishes-images-to-ghcr.md)).

See [`docs/adr`](docs/adr) for the reasoning behind each of these.

## Repository layout

```
.
├── openapi/                 # shared API contract (source of truth)
├── docs/adr/                # cross-cutting architecture decisions
├── docs/schema.md            # reference data model (non-binding, see ADR 0014)
├── backend-go/               # Go backend implementation
├── backend-rust/             # Rust backend implementation
├── backend-quarkus/          # Quarkus (Kotlin) backend implementation
├── backend-python/           # Python (FastAPI) backend implementation
├── frontend-react/           # React frontend implementation
├── frontend-svelte/          # Svelte frontend implementation
├── frontend-vue/             # Vue frontend implementation
├── frontend-angular/         # Angular frontend implementation
├── docker-compose.yml        # runs one backend + one frontend + db volume
└── .github/workflows/ci.yml  # builds/tests each implementation independently
```

## Running the default stack (backend-go + frontend-react)

```bash
cp backend-go/.env.example backend-go/.env   # fill in your OIDC provider + SMTP credentials
cp frontend-react/.env.example frontend-react/.env
docker compose up --build
```

Frontend: http://localhost:5173 · Backend: http://localhost:8080

See `backend-go/README.md`, `frontend-react/README.md`,
`frontend-svelte/README.md`, `frontend-vue/README.md`, and
`frontend-angular/README.md` for running each implementation standalone
(without Docker) for local development.

### Trying a different backend or frontend

`docker-compose.yml` builds whatever is at `backend-go/` and
`frontend-react/`. To try a different implementation — e.g. swap in
`frontend-svelte/`, `frontend-vue/`, or `frontend-angular/`, which all
have the same feature set as `frontend-react` with a different look and
color scheme — point the relevant service's `build.context` (and
`context`'s Dockerfile) at the other folder; every implementation exposes
the same port conventions and environment variables, so no other change
is needed.

## Configuration

All configuration is environment variables. Backend variable *names* are
shared by every backend implementation so swapping implementations
doesn't mean re-deriving config, but each implementation owns its own
`.env.example` — see `backend-go/.env.example` and
`frontend-react/.env.example` for the full lists (
[ADR 0011](docs/adr/0011-per-implementation-env-files.md)). The important
backend ones:

| Variable | Purpose |
|---|---|
| `OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_REDIRECT_URL` | Generic OIDC provider config (ADR 0004) |
| `ALLOWED_ORIGINS` | CORS allow-list, must include the frontend's origin |
| `SESSION_SECRET` | Server-side session signing/encryption key |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | Outgoing mail for share/mention notifications |
| `DATABASE_URL` | Database location; scheme selects the engine — a path/`sqlite://`/`file:` value for SQLite (default), or `postgres://...` for PostgreSQL (ADR 0013) |

Frontend variable names are also shared in spirit, but each frontend only
needs one backend-URL variable — see `frontend-react/.env.example` /
`frontend-svelte/.env.example` / `frontend-vue/.env.example`
(`VITE_API_BASE_URL`) and `frontend-angular/.env.example`
(`NG_APP_API_BASE_URL`, Angular's build-time config works differently —
see `frontend-angular/docs/decisions/0002-build-time-api-url.md`).

## Contributing a new implementation

1. Read `docs/adr/` and `AGENTS.md`/`CLAUDE.md` first.
2. Copy the shape of an existing implementation folder's `README.md` /
   `docs/decisions/` layout, don't invent a new documentation structure.
3. Implement `openapi/openapi.yaml` exactly — validate with
   `npx @stoplight/spectral-cli lint openapi/openapi.yaml` and add
   contract tests.
4. Model your schema close to [`docs/schema.md`](docs/schema.md) (ADR
   0014) — it's not enforced, but staying close keeps migrating data
   between implementations tractable. Note any deliberate deviation in
   your own `docs/decisions/`.
5. Add a `Dockerfile` and wire a CI job (see `.github/workflows/ci.yml`).
6. Do not change other implementations to make yours easier — the point
   of this repo is that they stay independent and swappable.

## License

See [`LICENSE`](LICENSE).
