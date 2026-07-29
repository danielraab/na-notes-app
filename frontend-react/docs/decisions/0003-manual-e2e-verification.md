# 3. Manual end-to-end verification instead of a component/E2E test suite

## Status

Accepted (revisit if this codebase grows significantly)

## Context

`src/utils` has unit tests (pure functions: mention parsing/insertion), but
there is no Playwright/Testing-Library suite exercising the actual UI
(login flow, note CRUD, sharing, infinite scroll) end-to-end.

## Decision

For the initial implementation, UI behavior was verified manually against
a real `backend-go` instance and a mock OIDC provider issuing real signed
ID tokens (login → session cookie → note create/edit/delete → dashboard
listing → share dialog → public link), driven via a headless browser.
This caught a real bug (`internal/users` scanning a TEXT `created_at`
column directly into `time.Time`, which compiles but fails at query time)
that unit tests alone had not covered.

No E2E suite was committed, since scripting one properly (test backend +
mock IdP fixtures) is a larger investment than this pass's scope.

## Consequences

- Regressions in the login → dashboard → editor → sharing flow won't be
  caught by `npm test` alone.
- If this frontend grows, add a Playwright suite (the sandbox this was
  built in already has Chromium available at
  `/opt/pw-browsers/chromium`) covering at minimum: anonymous dashboard →
  login → create note → edit note (optimistic-concurrency conflict) →
  share note → revoke share → public link view.
