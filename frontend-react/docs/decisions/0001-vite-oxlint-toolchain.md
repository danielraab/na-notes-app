# 1. Vite + oxlint toolchain

## Status

Accepted

## Context

Needed a fast, low-config dev/build toolchain and a linter for a
TypeScript React SPA with no server-side rendering requirement.

## Decision

- **Vite** for dev server + production build (esbuild/rollup under the
  hood, native ESM dev server, fast HMR).
- **oxlint** (Rust-based, config in `.oxlintrc.json`) instead of ESLint —
  scaffolded by `npm create vite@latest ... --template react-ts` and kept
  as-is: it's fast and covers the React hooks rules we care about
  (`react/rules-of-hooks`).
- `tsconfig.app.json` (from the same scaffold) enables `noUnusedLocals`,
  `noUnusedParameters`, `verbatimModuleSyntax`, and `erasableSyntaxOnly` —
  all code in this project must satisfy these (no TS `enum`, no
  constructor parameter properties, explicit `import type` for type-only
  imports).

## Consequences

- No Babel/webpack config to maintain.
- `erasableSyntaxOnly` ruled out some common TS patterns (e.g. `enum`,
  parameter-property constructors) — this codebase uses string-literal
  union types and explicit field assignment instead (see
  `src/api/client.ts`'s `ApiError`/`VersionConflictError` classes).
