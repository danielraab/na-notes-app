# 3. No component/E2E test suite; behavior verified manually

## Status

Accepted

## Context

`src/utils/` (mention parsing/insertion, task-list normalization) is
pure logic with `vitest` unit tests. The rest of the app — routing, the
editor, sharing, the dashboard feed — is UI behavior that would need a
component-testing setup (e.g. Vue Test Utils + jsdom) or a browser-based
E2E runner (Playwright) to cover automatically. Neither exists yet for
this implementation, matching `frontend-react` and `frontend-svelte`'s
own `0003-manual-e2e-verification.md`.

## Decision

UI behavior (dashboard feed, login/logout, note create/edit/delete,
sharing, public links, mention autocomplete, task-list toggling, light
and dark mode) was verified manually against a running backend during
development — including with mocked API responses via a headless browser
for flows that need seeded data — rather than adding an automated
component/E2E suite in this pass.

## Consequences

- No regression safety net for UI wiring beyond manual re-verification
  after a change; the `src/utils/` unit tests remain the only automated
  guard for note logic.
- If this implementation grows meaningfully, revisit and add Vue Test
  Utils for component-level coverage of the editor and sharing flows.
