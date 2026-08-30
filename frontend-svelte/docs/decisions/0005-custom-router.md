# 5. Hand-rolled router instead of a routing library

## Status

Accepted

## Context

The app needs exactly four routes: `/`, `/notes/new`, `/notes/:id`, and
`/shared/:token`, all client-side (the backend never renders HTML). Every
mainstream Svelte routing option at the time of writing is either
SvelteKit itself (a much bigger framework choice this project isn't
making — this is a plain Vite SPA, matching `frontend-react`'s plain
`react-router-dom` SPA setup) or a third-party library
(`svelte-routing`, `svelte-spa-router`) that is unmaintained or
hash-based routing by default.

## Decision

`src/lib/router.ts` is a ~60-line module: a Svelte `writable` store
(`currentPath`) synced to `window.location.pathname` via `popstate`, a
`navigate(to, { replace })` function wrapping `pushState`/`replaceState`,
a `matchRoute(pattern, path)` helper for `:param` segments, and a `link`
Svelte action that intercepts plain left-clicks on `<a>` tags for
client-side navigation (skipping modifier-key clicks, `target="_blank"`,
and external URLs). `App.svelte` matches `$currentPath` against the four
routes directly with `{#if}`/`{:else if}`.

## Consequences

- Zero routing dependencies, nothing to audit for supply-chain or version
  churn beyond Svelte/Vite themselves.
- No nested routes, no route guards/middleware, no lazy-loaded route
  chunks — none of that is needed for four flat routes. If the route list
  grows meaningfully more complex, revisit and consider a real router
  (SvelteKit or a maintained library) instead of growing this by hand.
- `nginx.conf`'s `try_files ... /index.html` fallback (needed for deep
  links on a hard refresh) works the same as it would with any
  History-API router — this isn't hash-based routing.
