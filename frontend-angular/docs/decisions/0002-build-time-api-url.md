# 2. Backend API URL is a build-time value, via Angular's environments pattern

## Status

Accepted

## Context

The frontend needs to know the backend's base URL. `frontend-react` and
`frontend-svelte` both bake this in at build time via Vite's
`import.meta.env.*` inlining (their own `0002-build-time-api-url.md`),
rather than reading it at container runtime. Angular has no
`import.meta.env` — its framework-native equivalent, predating Vite, is
the `src/environments/` file-replacement pattern.

## Decision

- `src/environments/environment.ts` exports `{ apiBaseUrl: string }` and
  is imported directly by `Api` (`src/app/api/api.ts`) — no
  `angular.json` `fileReplacements` indirection, since this app only ever
  builds one configuration (there's no separate "staging"/"production"
  backend to switch between).
- This file is **gitignored**, with `environment.example.ts` committed in
  its place — the same "copy, then edit" shape as the other two
  frontends' `.env.example`, just under a different filename since
  Angular doesn't read a `.env` file. `cp
  src/environments/environment.example.ts
  src/environments/environment.ts` and edit `apiBaseUrl` for local `ng
  serve` against a non-default backend.
- The `Dockerfile` doesn't rely on that copy step: it regenerates
  `environment.ts` from scratch via `ARG NG_APP_API_BASE_URL` (default
  `http://localhost:8080/api`, mirroring the other frontends' `ARG
  VITE_API_BASE_URL` default) piped through `printf` right before `npm
  run build`, so the image never depends on whatever a previous local
  edit happened to leave in that gitignored file.

## Consequences

- Same trade-off the other two frontends already accepted: one built
  image is pinned to one backend URL; pointing at a different backend
  means rebuilding.
- Editing `environment.ts` directly (instead of exporting a function that
  reads `process.env`) keeps this a plain, statically-analyzable TS
  module — `Api` imports a constant, not a runtime lookup — consistent
  with how the CLI's own scaffolding expects `environments/` to be used.
