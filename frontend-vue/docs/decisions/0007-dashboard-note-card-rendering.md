# 7. Dashboard note cards render the full note

## Status

Accepted

## Context

Root ADR 0012 changed the dashboard feed to return each note's full
`contentMarkdown` instead of a plain-text `excerpt`. The dashboard
renders complete notes as rich text in the card grid. That raises three
frontend questions: how to render Markdown compactly, how to keep long
notes from dominating the grid, and how to keep the card clickable now
that it contains rendered `<a>` tags — the same questions
`frontend-react`'s `0007-dashboard-note-card-rendering.md` answered;
this implementation reaches the same answers for the same reasons.

## Decision

- **Same render path, compact skin.** Cards render through the existing
  `MarkdownView` (ADR 0004) — no second path. `MarkdownView` accepts an
  optional `class` prop; the card passes `markdown-view--card`, a variant
  in `app.css` that scales headings down to body size and tightens block
  spacing so a note preview stays card-sized.
- **Clip, don't truncate.** The card body is wrapped in `.note-card-clip`
  (`max-height` + `overflow: hidden`) with a bottom gradient fade
  (`.note-card-fade`), shown only when a `ResizeObserver` detects the
  rendered content actually overflows. The full Markdown is in the DOM;
  only its height is bounded. Truncation stays out of the payload (ADR
  0012) and out of JS.
- **CSS grid**, not masonry (see ADR 0006) — cards fill left-to-right,
  top-to-bottom in reading order, unlike `frontend-react`'s
  CSS-columns masonry.
- **The card is a `<div>`, the title is the link.** A rendered note can
  contain `<a>` tags, and nesting `<a>` inside `<a>` is invalid HTML, so
  the card can't be a single `<RouterLink>`. The `<h3>` title is the
  `<RouterLink>` (keyboard/screen-reader navigation). A mouse-only
  `@click` on the card `<div>` navigates as a convenience but bails when
  the click lands on a link or on an active text selection.

## Consequences

- No new dependencies.
- Keyboard users navigate via the title link; the whole-card click is
  mouse sugar only, so it intentionally has no `role`/`tabindex`.
- If a masonry look is wanted later, this is the place `.note-grid` would
  be swapped for CSS columns or a JS layout.
