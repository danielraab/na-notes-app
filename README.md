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
- `frontend-<tech>/` — one frontend implementation (e.g. `frontend-react`).
  Talks to whichever backend is configured, only via the REST API.
- `openapi/openapi.yaml` — the single API contract every backend
  implements and every frontend consumes. Lives outside every
  implementation folder on purpose (ADR 0003).
- `docs/adr/` — cross-cutting architecture decisions that apply to every
  implementation (auth, pagination, concurrency, sharing, etc).
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
- Every backend and every frontend builds its own Docker image.

See [`docs/adr`](docs/adr) for the reasoning behind each of these.

## Repository layout

```
.
├── openapi/                 # shared API contract (source of truth)
├── docs/adr/                # cross-cutting architecture decisions
├── backend-go/               # Go backend implementation
├── frontend-react/           # React frontend implementation
├── docker-compose.yml        # runs one backend + one frontend + db volume
└── .github/workflows/ci.yml  # builds/tests each implementation independently
```

## Running the default stack (backend-go + frontend-react)

```bash
cp .env.example .env   # fill in your OIDC provider + SMTP credentials
docker compose up --build
```

Frontend: http://localhost:5173 · Backend: http://localhost:8080

See `backend-go/README.md` and `frontend-react/README.md` for running
each implementation standalone (without Docker) for local development.

### Trying a different backend or frontend

`docker-compose.yml` builds whatever is at `backend-go/` and
`frontend-react/`. To try a different implementation once one exists,
point the relevant service's `build.context` (and `context`'s Dockerfile)
at the other folder — every implementation exposes the same port
conventions and environment variables, so no other change is needed.

## Configuration

All configuration is environment variables, shared by every backend so
swapping implementations doesn't mean re-deriving config. See
`.env.example` for the full list; the important ones:

| Variable | Purpose |
|---|---|
| `OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_REDIRECT_URL` | Generic OIDC provider config (ADR 0004) |
| `ALLOWED_ORIGINS` | CORS allow-list, must include the frontend's origin |
| `SESSION_SECRET` | Server-side session signing/encryption key |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | Outgoing mail for share/mention notifications |
| `DATABASE_PATH` | SQLite file path inside the backend container |

## Contributing a new implementation

1. Read `docs/adr/` and `AGENTS.md`/`CLAUDE.md` first.
2. Copy the shape of an existing implementation folder's `README.md` /
   `docs/decisions/` layout, don't invent a new documentation structure.
3. Implement `openapi/openapi.yaml` exactly — validate with
   `npx @stoplight/spectral-cli lint openapi/openapi.yaml` and add
   contract tests.
4. Add a `Dockerfile` and wire a CI job (see `.github/workflows/ci.yml`).
5. Do not change other implementations to make yours easier — the point
   of this repo is that they stay independent and swappable.

## License

See [`LICENSE`](LICENSE).
