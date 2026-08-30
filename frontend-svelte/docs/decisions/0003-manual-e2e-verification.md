# 3. Manual end-to-end verification instead of a component/E2E test suite

## Status

Accepted (revisit if this codebase grows significantly)

## Context

`src/utils` has unit tests (pure functions: mention parsing/insertion,
task-list parsing), but there is no component/E2E suite exercising the
actual UI (login flow, note CRUD, sharing, infinite scroll, mentions,
task-list toggling) against a real backend.

## Decision

For this implementation, UI behavior was verified manually with a
headless Chromium session driving the built app against a **mocked** API
(Playwright's request interception standing in for `backend-go`, since no
live backend + OIDC fixture was set up for this pass) — covering: the
logged-out dashboard and sample note, login gating on `/notes/new` and
`/shared/:token`, the logged-in dashboard grid with rendered Markdown,
task-checkbox toggling (optimistic update, re-fetch, save), the editor's
Write/Preview toggle, `@`-mention autocomplete, and the share dialog
(existing shares, revoke, public link). Both light and dark color schemes
were checked visually. No live-backend / real-OIDC pass was done (compare
`frontend-react/docs/decisions/0003-manual-e2e-verification.md`, which
did have one) — that remains open.

No E2E suite was committed, since scripting one properly (test backend +
mock IdP fixtures, or a fixture-driven Playwright suite) is a larger
investment than this pass's scope.

## Consequences

- Regressions in the login → dashboard → editor → sharing flow against a
  **real backend** won't be caught by `npm test` or this pass's manual
  check alone — the manual check only proved the frontend calls the
  documented contract correctly and renders/reacts as expected, not that
  a real `backend-go` instance answers those calls the way the mocks did.
- If this frontend grows, add a Playwright suite (Chromium is available at
  `/opt/pw-browsers/chromium` in this project's sandboxes) covering at
  minimum: anonymous dashboard → login → create note → edit note
  (optimistic-concurrency conflict) → share note → revoke share → public
  link view, against a real backend and mock IdP as `frontend-react` did.
