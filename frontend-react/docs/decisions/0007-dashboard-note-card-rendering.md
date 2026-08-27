# 7. Dashboard note cards render the full note

## Status

Accepted

## Context

Root ADR 0012 changed the dashboard feed to return each note's full
`contentMarkdown` instead of a plain-text `excerpt`. The dashboard now
renders complete notes as rich text in the card grid. That raised three
frontend questions: how to render Markdown compactly, how to keep long
notes from dominating the grid, and how to keep the card clickable now
that it contains rendered `<a>` tags.

## Decision

- **Same render path, compact skin.** Cards render through the existing
  `MarkdownView` (ADR 0004) — no second path. `MarkdownView` gained an
  optional `className` prop; the card passes `markdown-view--card`, a
  variant in `index.css` that scales headings down to body size and
  tightens block spacing so a note preview stays card-sized.
- **Clip, don't truncate.** The card body is wrapped in
  `max-h-56 overflow-hidden` with a bottom gradient fade. The full
  Markdown is in the DOM; only its height is bounded. Truncation stays out
  of the payload (ADR 0012) and out of JS.
- **Masonry via CSS columns.** `NoteGrid` uses `columns-[240px]` with
  `break-inside-avoid` for the Google-Keep ragged-top look, rather than a
  JS masonry library. Trade-off: cards fill top-to-bottom per column, so
  reading order is not strictly left-to-right newest-first.
- **The card is a `<div>`, the title is the link.** A rendered note can
  contain `<a>` tags, and nesting `<a>` inside `<a>` is invalid HTML, so
  the card can't be a single `<Link>`. The `<h3>` title is the `<Link>`
  (keyboard/screen-reader navigation). A mouse-only `onClick` on the card
  `<div>` navigates as a convenience but bails when the click lands on a
  link or on an active text selection.

## Consequences

- No new dependencies.
- Keyboard users navigate via the title link; the whole-card click is
  mouse sugar only, so it intentionally has no `role`/`tabindex`.
- If true left-to-right masonry ordering is needed later, this is the
  place it would be swapped for a JS layout.
