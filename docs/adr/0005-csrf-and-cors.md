# 5. CSRF via double-submit cookie, strict CORS

## Status

Accepted

## Context

Sessions are carried in an HttpOnly cookie (ADR 4). Cookies are sent
automatically by browsers on cross-site requests, so without CSRF
protection any site could trigger authenticated state-changing requests
against the backend.

## Decision

- **CSRF**: double-submit cookie pattern. On session creation, the backend
  also sets a second, readable (non-HttpOnly) cookie, e.g. `csrf_token`,
  containing a random value. The frontend reads this cookie and sends its
  value back in an `X-CSRF-Token` request header on every state-changing
  request (`POST`/`PUT`/`PATCH`/`DELETE`). The backend rejects the request
  unless the header matches the cookie. `GET`/`HEAD` requests are exempt
  (must stay side-effect free).
- **CORS**: the backend only allows the exact configured frontend
  origin(s) (`ALLOWED_ORIGINS` env var, comma-separated), with
  `credentials: true`. Wildcard `*` origins are never used together with
  credentialed requests. Only the methods/headers actually used by the API
  are allowed.

## Consequences

- Every backend must implement the same header/cookie names
  (`csrf_token` cookie, `X-CSRF-Token` header) so a frontend implementation
  doesn't need per-backend logic.
- Local dev requires `ALLOWED_ORIGINS` to include the frontend dev server
  origin (documented per implementation).
- Public, read-only endpoints (e.g. viewing a publicly shared note) are
  unauthenticated `GET`s and are naturally exempt from CSRF checks.
