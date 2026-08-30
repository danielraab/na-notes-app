# 6. Plain CSS instead of a utility framework, and a distinct color scheme

## Status

Accepted

## Context

`frontend-react` uses Tailwind CSS v4. This implementation is meant to
have the same feature set but is explicitly allowed to differ in look and
feel and is required to use a different color scheme (product ask for
this pass) — a good opportunity to also make a different, equally valid
styling choice rather than reaching for Tailwind again.

## Decision

- **Plain CSS** (`src/app.css`): a set of semantic custom-property design
  tokens (`--canvas`, `--canvas-subtle`, `--fg`, `--fg-muted`, `--line`,
  `--accent`, `--danger`, …) switched via `prefers-color-scheme`, plus a
  small set of reusable classes (`.btn`/`.btn-primary`/`.btn-danger`,
  `.field`, `.page`, `.note-card`, `.markdown-view`, …) that components
  reference by name. No CSS-in-JS, no CSS modules, no Tailwind/UnoCSS.
- **A different palette and shape language, not just different hex
  values**: a teal accent (`#0f766e` light / `#5eead4` dark) on a
  warm-neutral canvas, pill-shaped buttons instead of rounded rectangles,
  card shadows instead of borders-only, a serif typeface for headings and
  note titles (`var(--font-serif)`) against sans-serif body text — visibly
  a different product skin from `frontend-react`'s violet/rounded-rectangle
  Tailwind look, while implementing the identical feature set.
- **CSS grid, not masonry**, for the dashboard (`.note-grid`:
  `repeat(auto-fill, minmax(15.5rem, 1fr))`) rather than
  `frontend-react`'s CSS-columns masonry (root cause: no functional
  requirement ties the two together; a plain grid is simpler in hand-written
  CSS and reads top-to-bottom left-to-right, unlike columns).

## Consequences

- No build-time CSS framework dependency; `src/app.css` is the only
  stylesheet and is plain enough to read top-to-bottom.
- Restyling means editing the token values and the handful of classes in
  one file, same "one place to change the palette" property Tailwind's
  `@theme` block gave `frontend-react`.
- No utility-class authoring speed for one-off layout tweaks — a few
  components (`DashboardPage.svelte`, `PublicNotePage.svelte`) needed a
  couple of small named classes (`.preview-row`, `.public-note-title`)
  that a utility framework would have inlined instead. Acceptable at this
  app's size.
