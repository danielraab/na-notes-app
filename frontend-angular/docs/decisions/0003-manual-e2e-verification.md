# 3. Manual end-to-end verification instead of a component/E2E test suite

## Status

Accepted (revisit if this codebase grows significantly)

## Context

`src/app/utils` has unit tests (pure functions: mention parsing/insertion,
task-list parsing, ported verbatim from `frontend-react`/`frontend-svelte`
since they're framework-agnostic), but there is no component/E2E suite
exercising the actual UI against a real backend — same situation
`frontend-react` and `frontend-svelte` are in (see their own
`0003-manual-e2e-verification.md`).

## Decision

For this implementation, UI behavior was verified with `ng serve` and a
headless Chromium session (Playwright, driving the pre-installed
`/opt/pw-browsers/chromium`) against a **mocked** API (route interception
standing in for `backend-go`, since no live backend + OIDC fixture was set
up for this pass) — covering:

- The logged-out dashboard (sample callout + `NotePreview`) and the
  logged-in dashboard grid rendering full note Markdown, in both light and
  dark `prefers-color-scheme`.
- Creating a new note: title + Markdown body, `@`-mention autocomplete
  (typing `@gra`, selecting a suggestion, confirming the inserted
  `@[Grace Hopper](u2)` token and `mentionedUserIds` on the `POST /notes`
  payload), Save, and the redirect to `/notes/:id`.
- Task-list checkboxes on a dashboard card: clicking one produced the
  expected `PUT /notes/:id` body with the toggled line and did **not**
  navigate the card (confirming `stopPropagation`).
- The share dialog (existing public link, revoke button) opened from the
  editor.
- The public read-only note view (`/shared/:token`).

This pass caught a real bug worth recording: binding sanitized markdown
HTML via plain `[innerHTML]="html()"` (a string) runs it back through
**Angular's own** `DomSanitizer` in addition to the app's DOMPurify pass,
and Angular's default sanitizer strips `<input>` elements — silently
dropping every task-list checkbox. Fixed in `MarkdownView` by wrapping the
already-DOMPurify-sanitized string in `sanitizer.bypassSecurityTrustHtml()`
(see [`0004-markdown-rendering.md`](0004-markdown-rendering.md)); worth
flagging for anyone porting `MarkdownView`-shaped code between frameworks.

No live-backend / real-OIDC pass was done, and the Docker image build
itself is unverified — this sandbox's Docker daemon could not be started
(`ulimit: operation not permitted` inside the sandbox) — even though `npm
run build`, `npx tsc -b`, `npm test`, and `npx oxlint` all pass and the
`Dockerfile` mirrors the other two frontends' proven-working multi-stage
nginx pattern exactly.

## Consequences

- Regressions in the login → dashboard → editor → sharing flow against a
  **real backend**, and the Docker image build itself, won't be caught by
  `npm test` or this pass's manual check — the manual check only proved
  the frontend calls the documented contract correctly and renders/reacts
  as expected against mocked responses.
- If this frontend grows, add a Playwright suite covering at minimum:
  anonymous dashboard → login → create note → edit note (optimistic-
  concurrency conflict) → share note → revoke share → public link view,
  against a real backend and mock IdP, and confirm the Docker image
  actually builds and serves in an environment with a working Docker
  daemon.
