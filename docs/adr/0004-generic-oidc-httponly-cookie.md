# 4. Generic OIDC login with server-side session in an HttpOnly cookie

## Status

Accepted

## Context

Login must work against "a universal OAuth provider" rather than being
locked to one vendor, so any implementation can be pointed at whatever
identity provider a developer already has (Google, Microsoft, Keycloak,
Auth0, Authentik, ...). The frontend must never handle raw tokens (XSS
would leak them).

## Decision

- Every backend implements the standard **OIDC Authorization Code flow
  with PKCE**, configured entirely through environment variables
  (`OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`,
  `OIDC_REDIRECT_URL`, `OIDC_SCOPES`). No provider-specific code is
  allowed in application logic — any standards-compliant OIDC provider
  must work unmodified.
- The backend is the OAuth client. It performs the code exchange
  server-side, validates the ID token, and creates its own server-side
  session (a random opaque session ID, stored server-side — DB or
  in-memory, backend's choice — mapped to the user).
- The session identifier is set as a cookie with `HttpOnly`, `Secure`
  (in non-dev environments), and `SameSite=Lax` attributes. The frontend
  JavaScript never sees the session token or any OIDC/OAuth token; it only
  ever sees "logged in" state via a `/api/auth/me` call.
- Logout invalidates the server-side session and clears the cookie.

## Consequences

- Frontends are identical regardless of which OIDC provider is configured
  — they only ever call `/api/auth/login`, `/api/auth/callback`,
  `/api/auth/logout`, `/api/auth/me`.
- Session storage/expiry is a backend implementation detail documented in
  that backend's own `docs/decisions/`.
- Because the cookie is HttpOnly, it cannot be read or exfiltrated by
  injected JS, which is also why CSRF protection is mandatory (see ADR 5).
