# 2. Backend API URL is a build-time value, via Angular's environments pattern

## Status

Accepted (revised — see "History" below)

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
- **This file is committed**, with a `http://localhost:8080/api` default,
  matching backend-go's own default port. For local `ng serve` against a
  non-default backend, edit `apiBaseUrl` directly.
- The `Dockerfile` overwrites it at build time via `ARG
  NG_APP_API_BASE_URL` (same default) piped through `printf` right before
  `npm run build`, mirroring the other frontends' `ARG VITE_API_BASE_URL`.

## History

The first version of this decision gitignored `environment.ts` and
committed `environment.example.ts` in its place, modeled directly on the
other two frontends' `.env.example` → `.env` copy-and-edit convention.
That broke CI immediately: `frontend-react`/`frontend-svelte`'s `.env` is
read by Vite's dev/build tooling, never imported by application source,
so a missing `.env` just falls back to a default inside `client.ts`.
`environment.ts` here is a real ES module `api.ts` does `import {
environment } from '../../environments/environment'` on — with the file
gitignored, a fresh CI checkout has no such module, and `tsc -b` fails
with `TS2307: Cannot find module '../../environments/environment'` before
a single test runs. The fix is the "committed with a default" shape
above, which is also just the standard, long-established Angular
convention for this file (predating this project) — the gitignore-it
instinct was importing a Vite-shaped mental model into a spot where
Angular's own idiom already had the right answer.

## Consequences

- Same trade-off the other two frontends already accepted: one built
  image is pinned to one backend URL; pointing at a different backend
  means rebuilding.
- Editing `environment.ts` directly (instead of exporting a function that
  reads `process.env`) keeps this a plain, statically-analyzable TS
  module — `Api` imports a constant, not a runtime lookup — consistent
  with how the CLI's own scaffolding expects `environments/` to be used.
- A local edit to `apiBaseUrl` for pointing at a different backend will
  show up as an uncommitted change to a tracked file (`git status`) —
  unlike a gitignored `.env`. That's an accepted, minor rough edge in
  exchange for the file actually existing when CI checks it out.
