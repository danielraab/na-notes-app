# CLAUDE.md — frontend-react

Read the repo root [`AGENTS.md`](../AGENTS.md) first — it applies here in
full. This file only adds React/TypeScript-specific notes.

- Never render note Markdown except through `MarkdownView` (`marked` +
  `DOMPurify.sanitize`) — see
  [`docs/decisions/0004-markdown-rendering.md`](docs/decisions/0004-markdown-rendering.md).
  No second "quick" render path that skips sanitization.
- The session/CSRF cookies are managed entirely by the backend; never add
  `localStorage`/`sessionStorage` token storage.
- `src/api/client.ts` is the only place that should call `fetch` against
  the backend — new endpoints get a typed function there, mirroring
  `src/api/types.ts` to the OpenAPI schema exactly.
- Respect `tsconfig.app.json`'s strictness: no TS `enum`, no constructor
  parameter properties (`erasableSyntaxOnly`), explicit `import type` for
  type-only imports (`verbatimModuleSyntax`), no unused locals/params.
- Run `npx tsc -b && npm run build && npx oxlint && npm test` before
  considering a change done.
- If a change touches `openapi/openapi.yaml` semantics, update
  `src/api/types.ts` and `src/api/client.ts` together, and flag that other
  frontend implementations need the equivalent change.
