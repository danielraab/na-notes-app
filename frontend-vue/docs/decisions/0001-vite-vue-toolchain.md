# 1. Vite + Vue 3 + TypeScript + oxlint toolchain

## Status

Accepted

## Context

This implementation needs a Vue toolchain equivalent to what
`frontend-react` (Vite + React + TypeScript + oxlint) and
`frontend-svelte` (Vite + Svelte + TypeScript + oxlint) already use, so
the three frontends stay comparable as a toolchain choice, not just a
component-model choice.

## Decision

- **Vite** as the dev server/bundler — same as both sibling frontends,
  `@vitejs/plugin-vue` for single-file-component (`.vue`) support.
- **Vue 3** with the **Composition API** and `<script setup lang="ts">`
  exclusively (no Options API, no class components) — the modern,
  TypeScript-friendly way to write Vue components, and the one with the
  best type inference for props/emits.
- **`vue-tsc`** for project-wide type-checking (`.vue` files need the Vue
  compiler to type-check their templates; plain `tsc` can't do this).
  `npm run build` runs `vue-tsc -b && vite build`, mirroring
  `frontend-react`'s `tsc -b && vite build`.
- **`oxlint`** for linting, with its `vue` plugin enabled — same linter as
  both sibling frontends, kept for consistency and speed.
- **`vitest`** for unit tests — same as both sibling frontends.

## Consequences

- Toolchain shape (Vite, TypeScript, oxlint, vitest) is identical across
  all three frontends; only the component model and its supporting
  libraries (routing, state) differ. This keeps a side-by-side comparison
  of the three meaningful.
- `vue-tsc -b` is slower than plain `tsc -b` since it also checks
  templates; acceptable at this project's size.
