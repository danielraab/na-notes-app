# CLAUDE.md — frontend-angular

Read the repo root [`AGENTS.md`](../AGENTS.md) first — it applies here in
full. This file only adds Angular/TypeScript-specific notes.

- Never render note Markdown except through `MarkdownView`
  (`src/app/components/markdown-view`, `marked` + `DOMPurify.sanitize`,
  then `DomSanitizer.bypassSecurityTrustHtml` on the already-sanitized
  result) — see
  [`docs/decisions/0004-markdown-rendering.md`](docs/decisions/0004-markdown-rendering.md).
  No second "quick" render path that skips sanitization, and never call
  `bypassSecurityTrustHtml` anywhere else without a DOMPurify pass
  immediately before it.
- The session/CSRF cookies are managed entirely by the backend; never add
  `localStorage`/`sessionStorage` token storage.
- `src/app/api/api.ts` (`Api`, an injectable `fetch`-based service — see
  [`docs/decisions/0007-fetch-based-api-service.md`](docs/decisions/0007-fetch-based-api-service.md))
  is the only place that should call `fetch` against the backend — new
  endpoints get a typed method there, mirroring `src/app/api/types.ts` to
  the OpenAPI schema exactly.
- This project is zoneless (no `zone.js`) and signals-first throughout —
  `signal`/`computed`/`linkedSignal`/`effect`/`afterRenderEffect`, signal
  `input()`/`output()`/`model()`, standalone components with
  `loadComponent` lazy routes. No `NgModule`, no `@Input()`/`@Output()`
  decorators, no RxJS-based state. See
  [`docs/decisions/0005-signals-router-input-binding.md`](docs/decisions/0005-signals-router-input-binding.md)
  for which primitive to reach for (in particular: `afterRenderEffect`,
  not `effect`, for anything that reads/writes real DOM nodes after a
  render).
- Styling is component-scoped `styleUrl` CSS files plus a small global
  token stylesheet (`src/styles.css`) — see
  [`docs/decisions/0006-component-styles-and-color-scheme.md`](docs/decisions/0006-component-styles-and-color-scheme.md).
  Reuse the existing global classes (`.btn`, `.field`, `.page`,
  `.callout`, `.markdown-view`, …) before inventing a new one, and note
  that `.markdown-view` content styling has to stay in the *global*
  stylesheet (component encapsulation can't reach `[innerHTML]`-injected
  content).
- Run `npx tsc -b && npm run build && npx oxlint && npm test` before
  considering a change done. `npm run build` also AOT type-checks every
  component template — a template error will surface there even if `tsc
  -b` alone is clean.
- If a change touches `openapi/openapi.yaml` semantics, update
  `src/app/api/types.ts` and `src/app/api/api.ts` together, and flag that
  other frontend implementations need the equivalent change.
