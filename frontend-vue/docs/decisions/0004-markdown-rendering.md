# 4. marked + DOMPurify for Markdown rendering

## Status

Accepted

## Context

Notes are always stored/edited as Markdown (product requirement); the
preview pane, read-only note view, and public share view all need to
render that Markdown as HTML. Rendered HTML can be viewed by people other
than the author (shares, public links), so it must not be a vector for
stored XSS. Both sibling frontends made this exact call already
(`frontend-react`/`frontend-svelte` `0004-markdown-rendering.md`); this
implementation follows the same approach rather than inventing a third
one, since it's a security decision, not a stylistic one.

## Decision

- `marked` converts Markdown to HTML (`MarkdownView.vue`).
- `DOMPurify.sanitize()` runs on that HTML before it's ever passed to
  `v-html`. This is not optional/best-effort — every markdown render path
  goes through `MarkdownView`, there is no second unsanitized render path.

## Consequences

- A malicious note body (e.g. `<img src=x onerror=alert(1)>` written as
  raw HTML inside the markdown source) is neutralized before rendering,
  even though `marked` itself passes raw inline HTML through by default.
- Rich text editing (optional per the product spec) was not implemented;
  if added later, it must still serialize to/from this same Markdown
  representation and render through `MarkdownView`, not a separate path.
