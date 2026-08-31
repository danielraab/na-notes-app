# 6. Plain CSS instead of a utility framework, and a distinct color scheme

## Status

Accepted

## Context

`frontend-react` uses Tailwind CSS v4 (violet accent, rounded-rectangle
outline buttons, CSS-columns masonry grid). `frontend-svelte` uses plain
CSS with a teal accent, pill-shaped buttons, a serif heading font, and a
CSS-grid dashboard. This implementation is meant to have the same feature
set but is free to differ in look, feel and color scheme — a third,
equally valid styling choice rather than reaching for Tailwind or
matching either sibling's palette.

## Decision

- **Plain CSS** (`src/app.css`): a set of semantic custom-property design
  tokens (`--canvas`, `--canvas-subtle`, `--fg`, `--fg-muted`, `--line`,
  `--accent`, `--danger`, …) switched via `prefers-color-scheme`, plus a
  small set of reusable classes (`.btn`/`.btn-primary`/`.btn-danger`,
  `.field`, `.page`, `.note-card`, `.markdown-view`, …) that components
  reference by name. No CSS-in-JS, no Vue `<style scoped>` per component,
  no Tailwind/UnoCSS — one shared stylesheet, same "one file to reskin
  the app" property both sibling frontends have.
- **A distinct palette and shape language**: a warm amber accent
  (`#b45309` light / `#fb923c` dark) on a cream canvas, square-cornered
  controls (not React's rounded rectangles or Svelte's pills), a single
  sans-serif typeface throughout (not Svelte's serif/sans-serif mix), and
  "index card" note cards — a colored left border instead of Svelte's
  shadow-only cards or React's border-only cards.
- **CSS grid**, not masonry, for the dashboard (`.note-grid`:
  `repeat(auto-fill, minmax(15.5rem, 1fr))`) — same layout strategy as
  `frontend-svelte` (simpler in hand-written CSS, reads top-to-bottom
  left-to-right), differing from `frontend-react`'s CSS-columns masonry.

## Consequences

- No build-time CSS framework dependency; `src/app.css` is the only
  stylesheet and is plain enough to read top-to-bottom.
- Restyling means editing the token values and the handful of classes in
  one file.
- No utility-class authoring speed for one-off layout tweaks; acceptable
  at this app's size, same tradeoff `frontend-svelte` already accepted.
