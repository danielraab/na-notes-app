# 4. marked + DOMPurify for Markdown rendering, explicitly trusted past Angular's own sanitizer

## Status

Accepted

## Context

Notes are always stored/edited as Markdown (product requirement); the
preview pane, read-only note view, dashboard card, and public share view
all need to render that Markdown as HTML. Rendered HTML can be viewed by
people other than the author (shares, public links), so it must not be a
vector for stored XSS. Same constraint `frontend-react` and
`frontend-svelte` solved with the same two libraries; kept identical here
since it's the correct answer regardless of framework.

## Decision

- `marked` converts Markdown to HTML; `DOMPurify.sanitize()` runs on that
  HTML before it's ever rendered — every markdown render path in this app
  goes through `MarkdownView` (`src/app/components/markdown-view`), there
  is no second unsanitized render path.
- `utils/task-list.ts` (ported verbatim, framework-agnostic) normalizes
  lenient `[ ]`/`[x]`/`[]` spellings before parsing, same as the other two
  frontends. `MarkdownView` accepts an `interactive` input; when true, an
  `afterRenderEffect` re-enables the (still-sanitized) checkboxes `marked`
  renders `disabled`, and a delegated `(click)` handler maps a toggled box
  back to its document-order index and emits a `toggleTask` output.
- **The sanitized HTML is explicitly marked trusted for Angular's own
  binding sanitizer**, via `DomSanitizer.bypassSecurityTrustHtml()`, right
  where `DOMPurify.sanitize()`'s result is produced. This is not
  optional/decorative: Angular auto-sanitizes any *plain string* bound to
  `[innerHTML]` with its **own**, separate sanitizer, which is stricter
  than DOMPurify's default profile — it strips `<input>` elements
  entirely. Binding the DOMPurify output directly as a string (i.e.
  skipping `bypassSecurityTrustHtml`) silently renders every task-list
  checkbox as if it didn't exist, no console warning, `<li>` and its text
  intact but no checkbox — this was caught during this pass's manual
  verification (see
  [`0003-manual-e2e-verification.md`](0003-manual-e2e-verification.md))
  and is exactly the kind of bug that only shows up by actually rendering
  the page, not by type-checking or unit-testing `MarkdownView` in
  isolation.

## Consequences

- A malicious note body (e.g. `<img src=x onerror=alert(1)>` written as
  raw HTML inside the markdown source) is neutralized by DOMPurify before
  `bypassSecurityTrustHtml` is ever reached — that call is safe specifically
  *because* DOMPurify already ran, not a way to skip sanitization. Never
  call `bypassSecurityTrustHtml` on markdown-derived HTML anywhere else in
  this codebase without the same DOMPurify pass immediately before it.
- Rich text editing (optional per the product spec) was not implemented;
  if added later, it must still serialize to/from this same Markdown
  representation and render through `MarkdownView`, not a separate path.
