# 6. Component-scoped CSS plus a global design-token stylesheet, and a distinct color scheme

## Status

Accepted

## Context

`frontend-react` uses Tailwind CSS v4 (utility classes, tokens in one
`index.css`). `frontend-svelte` uses one hand-written global stylesheet
with no component-scoped styles at all. Both are explicitly allowed to
differ in look and feel from each other, and the product ask for this
pass was a frontend with the same feature set but a **different color
scheme**. Angular's own idiom — and a genuine third point of comparison
among the three frontends — is per-component `styleUrl` files under
`ViewEncapsulation.Emulated` (the default), so this implementation uses
that rather than reaching for Tailwind or one global stylesheet again.

## Decision

- **Global tokens + primitives, in `src/styles.css`**: CSS custom
  properties (`--canvas`, `--canvas-subtle`, `--fg`, `--fg-muted`,
  `--line`, `--accent`, `--danger`, `--radius`, …) switched via
  `prefers-color-scheme`, plus the small set of classes shared across many
  components (`.btn`/`.btn-primary`/`.btn-danger`, `.field`, `.page`,
  `.callout`, `.note-card-meta`, `.suggestion-list`, …) — the same
  "token-driven design system" shape `frontend-react`'s `index.css` uses,
  just without Tailwind's `@apply`/utility layer.
- **Per-component `styleUrl` files for everything else**: `header.css`,
  `note-card.css`, `note-grid.css`, `share-dialog.css`,
  `note-editor-page.css`, etc. hold that component's own layout, scoped by
  Angular's emulated encapsulation so they can't leak into or be leaked
  into by another component's styles.
- **One deliberate exception**: `.markdown-view`/`.markdown-view--card`
  content styling (headings, lists, code blocks, task-list checkboxes)
  lives in the *global* `styles.css`, not in `markdown-view.css`. Emulated
  encapsulation only scopes elements the Angular template compiler itself
  produced — `MarkdownView` injects sanitized HTML via `[innerHTML]` at
  runtime (see
  [`0004-markdown-rendering.md`](0004-markdown-rendering.md)), which never
  gets the component's scoping attribute, so a scoped stylesheet simply
  wouldn't match it. This is called out at the top of `styles.css` so a
  future contributor doesn't "fix" it by moving those rules into
  `markdown-view.css` and silently losing all markdown styling.
- **A different palette and shape language, not just different hex
  values**: a burnt-orange/amber accent (`#c2410a` light / `#fb923c` dark)
  on a cool slate canvas, sharp corners (`--radius: 0.25rem`, vs.
  `frontend-react`'s rounded rectangles and `frontend-svelte`'s pills), a
  3px accent-colored left border on note cards and callouts instead of a
  full border or a shadow, and monospace, uppercase, letter-spaced text
  for metadata/badges (permission labels, timestamps, dialog section
  headers) — a deliberately more "technical/utilitarian" skin, visibly
  distinct from both existing frontends while implementing the identical
  feature set.
- **CSS Grid**, not masonry, for the dashboard (`note-grid.css`:
  `repeat(auto-fill, minmax(15.5rem, 1fr))`) — no functional requirement
  ties the layout algorithm to a specific frontend; a plain grid is the
  simplest choice here and needs no JS.

## Consequences

- Restyling the shared look means editing `styles.css`'s token block; a
  component-specific layout tweak means editing that component's own
  `.css` file — two clearly separated places instead of one large
  stylesheet or a utility-class soup.
- A new component that renders sanitized markdown-derived HTML must reuse
  `MarkdownView`, never add its own `[innerHTML]` binding — component-
  scoped CSS would silently fail to style it, on top of the sanitization
  concerns in ADR 0004.
- No CSS framework dependency; `styles.css` plus one `.css` per component
  is plain enough to read without build-step-specific knowledge.
