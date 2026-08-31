# 2. `VITE_API_BASE_URL` is resolved at build time

## Status

Accepted

## Context

The frontend needs to know the backend's base URL. Vite's standard
mechanism for this is `import.meta.env.*`, which is inlined into the
bundle when it's built (`vite build`), not read from the environment at
container start. This matches `frontend-react`'s and `frontend-svelte`'s
identical decision (their own `0002-build-time-api-url.md`) — same
tradeoff, made the same way here for consistency.

## Decision

`src/api/client.ts` reads `import.meta.env.VITE_API_BASE_URL` (defaulting
to `/api` if unset). The `Dockerfile` accepts it as a build `ARG`/`ENV` so
`docker build --build-arg VITE_API_BASE_URL=...` fixes it into the image
at build time.

## Consequences

- Pointing a built image at a different backend requires rebuilding it,
  not just restarting the container with a different environment
  variable. Acceptable for this project: `docker-compose.yml` already
  rebuilds on `up --build`, and this keeps the frontend a pure static
  asset bundle with nothing to template at container start.
- Local dev (`npm run dev`) picks up `.env` automatically via Vite's
  built-in env loading, no extra flags needed.
