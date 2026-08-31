# CLAUDE.md — frontend-vue

Read the repo root [`AGENTS.md`](../AGENTS.md) first — it applies here in
full. This file only adds Vue/TypeScript-specific notes.

- Never render note Markdown except through `MarkdownView.vue` (`marked` +
  `DOMPurify.sanitize`) — see
  [`docs/decisions/0004-markdown-rendering.md`](docs/decisions/0004-markdown-rendering.md).
  No second "quick" render path that skips sanitization.
- The session/CSRF cookies are managed entirely by the backend; never add
  `localStorage`/`sessionStorage` token storage.
- `src/api/client.ts` is the only place that should call `fetch` against
  the backend — new endpoints get a typed function there, mirroring
  `src/api/types.ts` to the OpenAPI schema exactly.
- This project uses the Composition API with `<script setup lang="ts">`
  exclusively — no Options API components, no class components.
- Shared auth state is the module-level `reactive()` singleton in
  `src/auth/auth.ts` (`useAuth()` returns it), not a provide/inject
  context or a Pinia store — there's exactly one piece of cross-cutting
  shared state in this app, and a singleton module is simpler than either.
  Per-feature state (the note feed, the editor) stays local to its
  component/composable; follow that pattern rather than introducing Pinia
  unless shared state genuinely grows beyond this.
- Routing is `vue-router` (`src/router/index.ts`), history mode, four
  routes plus a catch-all — see
  [`docs/decisions/0005-vue-router.md`](docs/decisions/0005-vue-router.md).
- Styling is plain CSS with custom-property tokens in `src/app.css`, no
  Tailwind/UnoCSS — see
  [`docs/decisions/0006-css-styling.md`](docs/decisions/0006-css-styling.md).
  Reuse the existing classes (`.btn`, `.field`, `.page`, `.markdown-view`,
  …) before inventing a new one.
- Respect `tsconfig.app.json`'s strictness: no unused locals/params,
  `verbatimModuleSyntax` (explicit `import type` for type-only imports).
- Run `npx vue-tsc -b && npx oxlint && npm test && npm run build` before
  considering a change done.
- If a change touches `openapi/openapi.yaml` semantics, update
  `src/api/types.ts` and `src/api/client.ts` together, and flag that other
  frontend implementations need the equivalent change.
