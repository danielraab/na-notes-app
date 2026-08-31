# 8. Task-list checkboxes render, and are toggleable from the dashboard

## Status

Accepted

## Context

Notes are GFM Markdown, so `- [ ] todo` / `- [x] done` lines are a
natural checklist. `marked` already emits `<input type="checkbox" disabled>`
for them and `DOMPurify`'s default allowlist keeps that element. The
product ask was to let a reader tick items off directly in the dashboard
card grid, without opening the editor — the same requirement
`frontend-react`'s `0008-interactive-task-lists.md` covers; this
implementation reuses its `utils/taskList.ts` logic verbatim (it's
framework-agnostic pure text manipulation) and wires it into Vue the
same way conceptually.

## Decision

- **No new render path.** Checkboxes come from `marked` + `DOMPurify` via
  `MarkdownView` (ADR 0004). `app.css` drops the list marker (keeping the
  list's normal indent, so nested checklists still nest) for any `li`
  whose first child is a checkbox.
- **Lenient input spelling.** `marked` only makes a checkbox from a
  *list item* whose text is exactly `[ ] ` / `[x] `, but notes routinely
  write bare `[ ] thing` lines with no `-`, and `[]` for "unchecked".
  `normalizeTaskLines` (`utils/taskList.ts`) rewrites those to the
  canonical `- [ ] ` / `- [x] ` before parsing — outside fenced code, and
  leaving real list items, inline `[ ]` and link-reference definitions
  (`[x]: url`) alone. The toggle helpers accept the same spellings and
  canonicalise `[]` on write.
- **Opt-in interactivity.** `MarkdownView` accepts an `interactive` prop.
  Without it (note view, public view, read-only shares) the boxes stay
  `disabled`, exactly as before. With it, a watcher re-enables the boxes
  after each render and a delegated `click` handler maps the clicked box
  to its document-order index and emits `toggle-task` — `stopPropagation`
  keeps the click off the card's navigate-on-click.
- **Source-line toggle, not re-serialization.** `setTaskItemChecked`
  rewrites the `index`-th `[ ]`/`[x]` in the Markdown source in place
  (skipping fenced code blocks, matching what `marked` renders). The note
  body is never round-tripped through a Markdown serializer.
- **Re-fetch to save.** `NoteSummary` carries neither the note `version`
  nor its mentions, so `NoteCard` re-fetches the note with `getNote`,
  applies the same toggle to the authoritative body, and saves it with
  `updateNote` (If-Match, ADR 0008 backend). The card updates
  optimistically and rolls back on failure.

## Consequences

- No new dependencies; no contract change (the dashboard already returns
  full `contentMarkdown` per root ADR 0012).
- Two requests per toggle (GET then PUT). Acceptable for an occasional
  click; if it becomes hot, add `version` to the feed payload instead.
- A toggle races a concurrent edit like any other save: the PUT can come
  back `409` and the toggle is silently rolled back. The dashboard has no
  conflict UI — the editor is where conflicts are surfaced.
- The index mapping assumes the card's Markdown and the freshly fetched
  Markdown have the same task items in the same order. A structural edit
  between render and click could toggle the wrong line; the blast radius
  is one checkbox.
