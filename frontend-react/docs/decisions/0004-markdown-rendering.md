# 4. marked + DOMPurify for Markdown rendering

## Status

Accepted

## Context

Notes are always stored/edited as Markdown (product requirement); the
preview pane, read-only note view, and public share view all need to
render that Markdown as HTML. Rendered HTML can be viewed by people other
than the author (shares, public links), so it must not be a vector for
stored XSS.

## Decision

- `marked` converts Markdown to HTML (`MarkdownView.tsx`).
- `DOMPurify.sanitize()` runs on that HTML before it's ever passed to
  `dangerouslySetInnerHTML`. This is not optional/best-effort — every
  markdown render path goes through `MarkdownView`, there is no second
  unsanitized render path.

## Consequences

- A malicious note body (e.g. `<img src=x onerror=alert(1)>` written as
  raw HTML inside the markdown source) is neutralized before rendering,
  even though `marked` itself passes raw inline HTML through by default.
- Rich text editing (optional per the product spec) was not implemented;
  if added later, it must still serialize to/from this same Markdown
  representation and render through `MarkdownView`, not a separate path.
