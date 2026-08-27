# 12. Dashboard feed carries full note Markdown

## Status

Accepted

## Context

The dashboard feed (`GET /api/notes`, ADR 0007) returned `NoteSummary`
objects whose `excerpt` field was a short **plain-text** string the
backend derived from the note body by stripping Markdown punctuation with
a regex. On the dashboard this rendered as mangled text: headings, lists,
emphasis, links and code were all destroyed before reaching the client,
so a note was never shown "correctly" in the overview.

We want the overview to show each note's real content as rich text.
Rendering Markdown to HTML is already a solved, security-reviewed concern
on the client side (ADR 0004: `marked` + `DOMPurify` via `MarkdownView`).
The backend must not grow a second rendering/preview code path that every
backend implementation would have to reproduce identically, and it must
never emit or store rendered HTML.

## Decision

- `NoteSummary` drops `excerpt` and instead carries `contentMarkdown` —
  the full, unmodified Markdown body, identical in meaning to
  `Note.contentMarkdown`. `NoteSummary` now differs from `Note` only by
  omitting `version` and `createdAt`.
- The server returns Markdown only. It performs no truncation, no
  stripping, and no HTML rendering for the feed. Any server-side excerpt
  logic is deleted.
- The dashboard renders each note client-side through the existing
  sanitized `MarkdownView` path (ADR 0004). Presentation concerns —
  clipping long notes, card sizing — are solved with CSS in the frontend,
  not by shortening the payload.
- Pagination (ADR 0007) is unchanged: same `cursor`/`limit` params, same
  `nextCursor`.

## Consequences

- Feed responses now scale with note size rather than being a fixed-length
  preview. Page size (`limit`, default 12, max 50) is the bound on how
  much this costs per request; revisit if notes grow large enough for this
  to matter.
- No server-side preview/excerpt logic to keep byte-identical across
  backend implementations, and no risk of a backend leaking HTML.
- Every backend implementation must return `contentMarkdown` (not
  `excerpt`) in `NoteSummary`; every frontend renders it itself.
