# frontend-angular

Angular implementation of the NA Notes frontend. Talks to whichever
backend is configured entirely over the REST API in
[`/openapi/openapi.yaml`](../openapi/openapi.yaml) — see the root
[`README.md`](../README.md) and [`/docs/adr`](../docs/adr) for the
cross-cutting rules this implementation follows (auth cookie handling,
CSRF, pagination, sharing, concurrency).

Same feature set as `frontend-react`/`frontend-svelte`, a different stack,
and a different color scheme/visual style — see
[`docs/decisions/`](docs/decisions) for why each choice was made.

## Stack

- **Angular 22**, the current release, using its newest idioms: standalone
  components, zoneless change detection, signals
  (`signal`/`computed`/`linkedSignal`/`effect`) for all state,
  `input()`/`output()`/`model()` instead of decorators, and route params
  bound straight to component inputs via `withComponentInputBinding()` —
  see [`docs/decisions/0001-angular-zoneless-toolchain.md`](docs/decisions/0001-angular-zoneless-toolchain.md)
  and [`0005-signals-router-input-binding.md`](docs/decisions/0005-signals-router-input-binding.md).
- The Angular CLI's own build tooling: `@angular/build:application`
  (esbuild) for builds, its Vite-based dev server for `ng serve`, and its
  built-in `@angular/build:unit-test` Vitest integration for `ng test`.
- `marked` + `dompurify` for rendering and sanitizing note Markdown (see
  [`docs/decisions/0004-markdown-rendering.md`](docs/decisions/0004-markdown-rendering.md)
  — this one has an Angular-specific gotcha worth reading).
- A plain `fetch`-based `Api` service instead of `HttpClient` — see
  [`docs/decisions/0007-fetch-based-api-service.md`](docs/decisions/0007-fetch-based-api-service.md).
- Component-scoped CSS plus a small global design-token stylesheet, with a
  distinct amber/slate color scheme — see
  [`docs/decisions/0006-component-styles-and-color-scheme.md`](docs/decisions/0006-component-styles-and-color-scheme.md).
- `oxlint` for linting the plain `.ts` modules (Angular's own AOT
  template type-checking, run as part of `ng build`, covers `.html`
  templates).

## Running locally

```bash
cd frontend-angular
npm install
cp src/environments/environment.example.ts src/environments/environment.ts   # edit apiBaseUrl if not localhost:8080
npm start
```

The dev server runs on `http://localhost:4200`. It must be pointed at a
running backend (see `../backend-go/README.md`) whose `ALLOWED_ORIGINS`
includes `http://localhost:4200` and whose `FRONTEND_URL` is also
`http://localhost:4200` (see `../backend-go/.env.example`).

**Use the same hostname (`localhost`) for both frontend and backend during
local development.** The session cookie is `SameSite=Lax`; mixing
`localhost` and `127.0.0.1` between the two makes browsers treat them as
different sites and silently drops the cookie on cross-origin requests.

## Configuration

- `src/environments/environment.ts`'s `apiBaseUrl` — the backend's API
  base URL. Gitignored; copy `environment.example.ts` to get started.
  Baked into the bundle at build time — rebuild to point at a different
  backend. See
  [`docs/decisions/0002-build-time-api-url.md`](docs/decisions/0002-build-time-api-url.md).

## Project layout

```
src/app/api/         # typed Api service (fetch-based) + request/response types mirroring the OpenAPI spec
src/app/auth/        # Auth: an injectable singleton exposing user/loading signals and login()/logout()
src/app/components/  # NoteCard, NoteGrid (infinite scroll), MarkdownEditor/View, ShareDialog, Header
src/app/pages/        # DashboardPage, NoteEditorPage, PublicNotePage (lazy-loaded routes)
src/app/utils/        # pure helpers (mention parsing/insertion, task-list parsing) — unit tested
src/environments/     # build-time apiBaseUrl config (see docs/decisions/0002)
```

## Testing

```bash
npm test        # ng test — Vitest, pure-function unit tests (src/app/utils)
npm run lint     # oxlint
npx tsc -b       # type-check
npm run build    # production build (also AOT type-checks every template)
```

There is no component/E2E test suite yet; UI behavior has been verified
manually (see
[`docs/decisions/0003-manual-e2e-verification.md`](docs/decisions/0003-manual-e2e-verification.md)).

## Docker

```bash
docker build -t na-notes-frontend-angular --build-arg NG_APP_API_BASE_URL=http://localhost:8080/api .
docker run --rm -p 4200:80 na-notes-frontend-angular
```

The image is a multi-stage build: `npm run build` produces static assets
under `dist/frontend-angular/browser`, served by nginx.
`NG_APP_API_BASE_URL` is a **build-time** argument, written into
`src/environments/environment.ts` before `ng build` runs — see
[`docs/decisions/0002-build-time-api-url.md`](docs/decisions/0002-build-time-api-url.md).

## Security notes specific to this implementation

- All note Markdown is rendered via `marked`, sanitized with `DOMPurify`,
  and then explicitly marked trusted for Angular's own `[innerHTML]`
  sanitizer (`MarkdownView`) — never trust markdown as safe HTML just
  because it came from our own API, since other users' shared/public
  notes render in your browser too. See
  [`docs/decisions/0004-markdown-rendering.md`](docs/decisions/0004-markdown-rendering.md)
  for why the `bypassSecurityTrustHtml` call is there and why it's safe.
- The session and CSRF tokens live in cookies set by the backend
  (`HttpOnly` for the session, readable for CSRF) — this app never stores
  either in `localStorage`/`sessionStorage`.
- Every mutating API call (`POST`/`PUT`/`DELETE`) automatically attaches
  the `X-CSRF-Token` header from the `csrf_token` cookie
  (`src/app/api/api.ts`).
