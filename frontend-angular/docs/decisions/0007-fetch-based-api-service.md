# 7. A plain `fetch`-based `Api` service instead of `HttpClient`

## Status

Accepted

## Context

Angular's idiomatic HTTP layer is `HttpClient` (`provideHttpClient()`,
injectable, RxJS-based). `frontend-react` and `frontend-svelte` both talk
to the backend with a hand-written `fetch` wrapper (`src/api/client.ts` in
each) that has to get several project-specific things exactly right: the
CSRF double-submit header on mutating requests (ADR 0005), a 204 response
with no body, and `updateNote`'s special case where a `409` response body
is a bare `Note`, not the `{error}` envelope every other endpoint uses
(ADR 0008).

## Decision

`src/app/api/api.ts` is an injectable (`providedIn: 'root'`) class,
`Api`, whose methods wrap `fetch` directly — effectively
`frontend-react`/`frontend-svelte`'s `client.ts` ported into an Angular
service, not rewritten against `HttpClient`. `ApiError` and
`VersionConflictError` are the same two error types (same shape,
ported verbatim) the other two frontends throw, so error handling in
`NoteEditorPage` (catching `VersionConflictError` for the optimistic-
concurrency conflict UI) reads the same across all three implementations.

## Consequences

- Reproduces the CSRF header, 204-body, and 409-bare-`Note` handling
  exactly, with zero risk of an `HttpClient` interceptor subtly changing
  that behavior (e.g. `HttpClient` parses JSON automatically in a way that
  would need extra work to special-case the 409 response the same way).
- Gives up `HttpClient`'s built-in testing utilities
  (`HttpClientTestingModule`) and RxJS operators (`retry`, `timeout`,
  cancellation via subscription teardown) — not used by any of this app's
  requests today. If a future endpoint needs those, reconsider this
  decision rather than hand-rolling them onto `fetch`.
- `Api` returns `Promise`s, not `Observable`s, which is what every caller
  in this codebase (constructors, `effect()` bodies, event handlers)
  already expects — consistent with `async`/`await` used throughout
  rather than mixing in RxJS subscriptions for HTTP alongside signals for
  state.
