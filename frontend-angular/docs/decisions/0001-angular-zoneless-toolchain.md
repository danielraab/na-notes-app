# 1. Angular CLI 22 (zoneless, signals, esbuild/Vite dev server) toolchain

## Status

Accepted

## Context

This implementation's whole point is to be the *current, idiomatic*
Angular answer to the same product spec `frontend-react` and
`frontend-svelte` already implement — not an Angular app written the way
Angular apps looked in 2018. As of this pass, `@angular/cli@22` is the
newest release actually installable and buildable in this repo's
environment (see "Consequences" for a wrinkle there).

## Decision

- Scaffolded with `ng new --zoneless --routing --style=css` (2025 file
  naming style, the CLI's current default: `app.ts`/`app.html` rather
  than `app.component.ts`, class names without a `Component`/`Service`
  suffix — see `App`, `Auth`, `Api` in this codebase).
- **Zoneless change detection** (`provideZonelessChangeDetection()`, no
  `zone.js` dependency) with **signals** as the reactivity primitive
  throughout — `signal`/`computed`/`linkedSignal`/`effect`, signal
  `input()`/`output()`/`model()` instead of `@Input()`/`@Output()`
  decorators or NgModules. This is the direction Angular itself has been
  moving since v17 and is what a new Angular app should use today.
- The CLI's new **`@angular/build:application`** builder (esbuild for
  production builds, a Vite-based dev server for `ng serve`) and its
  built-in **`@angular/build:unit-test`** Vitest integration — no
  hand-assembled webpack/Karma/Jasmine config, and the same test runner
  (`vitest`) `frontend-react`/`frontend-svelte` already use, so
  `src/app/utils/*.spec.ts` reads the same as their `*.test.ts`.
- Standalone components + `loadComponent` lazy routes throughout; no
  `NgModule` anywhere in this codebase.

## Consequences

- This sandbox's pinned Node (v22.22.2) is one patch below what
  `@angular/cli@22` requires (`^22.22.3`); development used `nvm` to
  install Node 24 LTS locally. The committed `Dockerfile` builds on
  `node:22-alpine`, which tracks the latest 22.x patch and is expected to
  satisfy this by the time the image is built — see
  [`0002-build-time-api-url.md`](0002-build-time-api-url.md) for the rest
  of the build.
- No `zone.js` patching means change detection only runs when a signal
  used in a template changes (or on an explicit trigger) — every
  component in this codebase drives its template state through signals
  precisely so that holds. A future contributor pushing plain mutable
  fields into a template would silently stop seeing updates; there is no
  zone-based safety net catching that here.
- Vitest as the unit-test runner means test files use global
  `describe`/`it`/`expect` (via `tsconfig.spec.json`'s
  `"types": ["vitest/globals"]`), same as the other two frontends.
