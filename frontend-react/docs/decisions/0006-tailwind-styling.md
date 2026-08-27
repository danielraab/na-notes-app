# 6. Tailwind CSS v4 for styling

## Status

Accepted (supersedes the hand-rolled `src/index.css` design system).

## Context

Styling was a single hand-maintained `src/index.css`: a set of CSS custom
properties for the light/dark palette plus ~40 bespoke BEM-ish class names
(`.note-card`, `.app-header__actions`, `.dialog-overlay`, …) that every
component referenced by string. Adding or restyling a component meant
round-tripping between the component and that file and inventing another
class name.

## Decision

- **Tailwind CSS v4** (`tailwindcss` + `@tailwindcss/vite`), wired in as a
  Vite plugin in `vite.config.ts`. No `tailwind.config.js` — v4 is
  configured from CSS.
- `src/index.css` now holds:
  - `@import 'tailwindcss';`
  - An `@theme inline` block that maps semantic color tokens
    (`--color-canvas`, `--color-canvas-subtle`, `--color-fg`,
    `--color-fg-muted`, `--color-line`, `--color-accent`,
    `--color-accent-fg`, `--color-danger`) onto plain CSS custom
    properties. The custom properties keep the **exact original
    light/dark values** and the `prefers-color-scheme` media query, so
    the theme still switches automatically with no `dark:` variants
    sprinkled through the markup.
  - A small `@layer components` block for the handful of primitives that
    genuinely repeat: `.page`, `.btn` / `.btn-primary` / `.btn-danger`,
    `.field`. These are composed from utilities with `@apply` — one
    definition instead of the same 12-class string in ~18 places.
  - `.markdown-view` element styles. The Markdown render path injects
    sanitized HTML via `dangerouslySetInnerHTML` (see
    [0004](0004-markdown-rendering.md)), so those elements can't carry
    utility classes and are styled here (still via `@apply`).
- Everything else — layout, spacing, one-off styling — is inline utility
  classes in the components.

## Consequences

- No `@tailwindcss/typography` plugin; `.markdown-view` styling is a short
  explicit list of element rules. Revisit if note formatting needs grow.
- `tailwindcss` / `@tailwindcss/vite` are **devDependencies** (build-time
  only, like `vite` itself). The Docker build runs `npm ci` (all deps)
  then `npm run build`, so this is fine.
- Production CSS is generated on demand from the classes actually used
  (~19 kB / ~4 kB gzip for the current app).
- The palette lives in `src/index.css` only. Components refer to it
  through semantic utilities (`bg-canvas`, `text-fg-muted`,
  `border-line`, …); a future restyle changes the eight token values in
  one place.
