# CLAUDE.md — frontend-svelte

Read the repo root [`AGENTS.md`](../AGENTS.md) first — it applies here in
full. This file only adds Svelte/TypeScript-specific notes.

- Never render note Markdown except through `MarkdownView.svelte`
  (`marked` + `DOMPurify.sanitize`) — see
  [`docs/decisions/0004-markdown-rendering.md`](docs/decisions/0004-markdown-rendering.md).
  No second "quick" render path that skips sanitization.
- The session/CSRF cookies are managed entirely by the backend; never add
  `localStorage`/`sessionStorage` token storage.
- `src/api/client.ts` is the only place that should call `fetch` against
  the backend — new endpoints get a typed function there, mirroring
  `src/api/types.ts` to the OpenAPI schema exactly.
- This project uses Svelte 5 runes (`$state`, `$derived`, `$effect`,
  `$props`), not the Svelte 4 `export let`/reactive-statement style, and
  not React-style hooks. Shared reactive state lives in `.svelte.ts`
  modules exporting a singleton (`src/auth/auth.svelte.ts`) or a factory
  function returning a getter-based object (`src/hooks/noteFeed.svelte.ts`)
  — follow those patterns for new shared state rather than introducing a
  Svelte store unless the state genuinely needs store semantics (see
  `src/lib/router.ts`, which does).
- Routing is the small hand-rolled router in `src/lib/router.ts`
  (`currentPath` store, `navigate()`, `matchRoute()`, the `link` action) —
  don't add a routing library; see
  [`docs/decisions/0005-custom-router.md`](docs/decisions/0005-custom-router.md).
- Styling is plain CSS with custom-property tokens in `src/app.css`, no
  Tailwind/CSS framework — see
  [`docs/decisions/0006-plain-css-styling.md`](docs/decisions/0006-plain-css-styling.md).
  Reuse the existing classes (`.btn`, `.field`, `.page`, `.markdown-view`,
  …) before inventing a new one.
- Run `npx svelte-check --tsconfig ./tsconfig.app.json && npx tsc -p
  tsconfig.node.json && npx oxlint && npm test && npm run build` before
  considering a change done.
- If a change touches `openapi/openapi.yaml` semantics, update
  `src/api/types.ts` and `src/api/client.ts` together, and flag that other
  frontend implementations need the equivalent change.
