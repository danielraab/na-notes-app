# 1. Vite + Svelte 5 + oxlint/svelte-check toolchain

## Status

Accepted

## Context

Needed a fast, low-config dev/build toolchain for a TypeScript Svelte SPA
with no server-side rendering requirement, comparable to
`frontend-react`'s Vite setup but idiomatic for Svelte.

## Decision

- **Vite** for dev server + production build, scaffolded with
  `npm create vite@latest -- --template svelte-ts`.
- **Svelte 5** using runes (`$state`, `$derived`, `$effect`, `$props`)
  throughout — the current idiomatic Svelte reactivity model, not the
  Svelte 4 `export let` / top-level reactive-statement style.
- **`svelte-check`** for type-checking, including `.svelte` template
  expressions — `tsc` alone can't check markup. `npm run build` and
  `npm run check` both run it before `tsc -p tsconfig.node.json` (the Vite
  config itself) and `vite build`.
- **oxlint** kept as the linter (same tool as `frontend-react`), but
  scoped to plain `.ts` modules (`src/api`, `src/auth`, `src/hooks`,
  `src/lib`, `src/utils`) via `.oxlintrc.json`'s `ignorePatterns`. oxlint
  doesn't parse `.svelte` files; `svelte-check` is the correctness net for
  those instead of pulling in `eslint-plugin-svelte`.

## Consequences

- No Babel/webpack config to maintain, same as `frontend-react`.
- Two separate tools cover "the whole codebase is checked" (`svelte-check`
  for `.svelte` + `.ts` template usage, `oxlint` for plain `.ts`) instead
  of one linter doing everything — acceptable since `svelte-check`'s
  diagnostics (unused vars, `state_referenced_locally`, a11y warnings,
  etc.) already cover most of what a linter would flag in `.svelte` files.
