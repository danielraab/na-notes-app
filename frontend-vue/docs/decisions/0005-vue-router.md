# 5. `vue-router` for routing

## Status

Accepted

## Context

The app needs four routes: `/`, `/notes/new`, `/notes/:id`, and
`/shared/:token`, all client-side (the backend never renders HTML), plus
a not-found fallback. `frontend-react` uses `react-router-dom`;
`frontend-svelte` hand-rolls a ~60-line router (its
`0005-custom-router.md`) because the mainstream Svelte routing options at
the time were either SvelteKit itself or an unmaintained/hash-based
library.

Vue's situation is different: `vue-router` is the official, actively
maintained router for Vue (maintained by the Vue core team, not a
third-party project), matches this app's Vite-SPA shape exactly (no
meta-framework like Nuxt required), and is the idiomatic default any Vue
developer would reach for.

## Decision

`vue-router` (`createWebHistory`) with a flat route table in
`src/router/index.ts`: `/` -> `DashboardPage`, `/notes/new` ->
`NoteEditorPage` (with a static `isNew: true` prop), `/notes/:id` ->
`NoteEditorPage` (route param passed as the `id` prop), `/shared/:token`
-> `PublicNotePage`, and a `/:pathMatch(.*)*` catch-all -> `NotFoundPage`.
Routes are lazy-loaded (`component: () => import(...)`) for per-page code
splitting. Navigation uses `<RouterLink>` and `useRouter()`/`useRoute()`
rather than raw History API calls.

## Consequences

- One well-maintained dependency to track, versus zero (Svelte's
  hand-rolled router) or `react-router-dom`. Given `vue-router` is
  official and Vue-idiomatic, this is judged the right tradeoff for this
  implementation rather than hand-rolling a Vue equivalent of the Svelte
  router.
- `nginx.conf`'s `try_files ... /index.html` fallback (needed for deep
  links on a hard refresh) is required exactly as it is for the other two
  frontends — `createWebHistory` is not hash-based routing.
- Per-route code splitting is "for free" via dynamic `import()`, which
  neither sibling frontend's routing setup does.
