# frontend-react

React implementation of the NA Notes frontend. Talks to whichever backend
is configured entirely over the REST API in
[`/openapi/openapi.yaml`](../openapi/openapi.yaml) — see the root
[`README.md`](../README.md) and [`/docs/adr`](../docs/adr) for the
cross-cutting rules this implementation follows (auth cookie handling,
CSRF, pagination, sharing, concurrency).

## Stack

- Vite + React 19 + TypeScript.
- `react-router-dom` for client-side routing.
- Tailwind CSS v4 (`@tailwindcss/vite`) for styling — palette tokens and a
  few `@apply` primitives live in `src/index.css`, everything else is
  inline utilities (see `docs/decisions/0006-tailwind-styling.md`).
- `marked` + `dompurify` for rendering and sanitizing note Markdown.
- `vitest` for unit tests.
- `oxlint` for linting.

See [`docs/decisions/`](docs/decisions) for the reasoning behind these
choices.

## Running locally

```bash
cd frontend-react
npm install
cp .env.example .env   # or: VITE_API_BASE_URL=http://localhost:8080/api npm run dev
npm run dev
```

The dev server runs on `http://localhost:5173`. It must be pointed at a
running backend (see `../backend-go/README.md`) whose `ALLOWED_ORIGINS`
includes `http://localhost:5173` and whose `FRONTEND_URL` is also
`http://localhost:5173` (see `../backend-go/.env.example`).

**Use the same hostname (`localhost`) for both frontend and backend during
local development.** The session cookie is `SameSite=Lax`; mixing
`localhost` and `127.0.0.1` between the two makes browsers treat them as
different sites and silently drops the cookie on cross-origin requests.

## Configuration

- `VITE_API_BASE_URL` — the backend's API base URL. Baked in at build
  time (standard Vite behavior); rebuild the image to point at a
  different backend.

## Project layout

```
src/api/        # typed API client + request/response types mirroring the OpenAPI spec
src/auth/       # auth context/hook: fetches /api/auth/me, exposes login()/logout()
src/components/ # NoteCard, NoteGrid (infinite scroll), MarkdownEditor/View, ShareDialog, Header
src/hooks/      # useNoteFeed: cursor-pagination state machine for the dashboard
src/pages/      # DashboardPage, NoteEditorPage, PublicNotePage
src/utils/      # pure helpers (mention parsing/insertion) — unit tested
```

## Testing

```bash
npm test        # vitest — pure-function unit tests (src/utils)
npm run lint     # oxlint
npx tsc -b       # type-check
npm run build    # production build
```

There is no component/E2E test suite yet; UI behavior has been verified
manually (see `docs/decisions/0003-manual-e2e-verification.md`).

## Docker

```bash
docker build -t na-notes-frontend-react --build-arg VITE_API_BASE_URL=http://localhost:8080/api .
docker run --rm -p 5173:80 na-notes-frontend-react
```

The image is a multi-stage build: `npm run build` produces static assets,
served by nginx. `VITE_API_BASE_URL` is a **build-time** argument — Vite
inlines `import.meta.env.*` values at build time, so the API URL is fixed
once the image is built (see `docs/decisions/0002-build-time-api-url.md`).

## Security notes specific to this implementation

- All note Markdown is rendered via `marked` and then sanitized with
  `DOMPurify` before being injected as HTML (`MarkdownView.tsx`) — never
  trust markdown as safe HTML just because it came from our own API,
  since other users' shared/public notes render in your browser too.
- The session and CSRF tokens live in cookies set by the backend
  (`HttpOnly` for the session, readable for CSRF) — this app never stores
  either in `localStorage`/`sessionStorage`.
- Every mutating API call (`POST`/`PUT`/`DELETE`) automatically attaches
  the `X-CSRF-Token` header from the `csrf_token` cookie (`src/api/client.ts`).
