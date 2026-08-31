# 2. Hand-rolled OIDC client instead of the `quarkus-oidc` extension

## Status

Accepted.

## Context

ADR 0004 (root) requires the *backend* to be the OAuth client for a
standards-compliant, generic OIDC provider: it performs the authorization
code + PKCE exchange itself, verifies the ID token, and creates its own
opaque server-side session — set as a `session` cookie with an exact
`HttpOnly`/`Secure`/`SameSite=Lax` contract shared byte-for-byte with
`backend-go` and `backend-rust`. ADR 0005 layers a specific CSRF
double-submit cookie (`csrf_token` / `X-CSRF-Token`) on top.

Quarkus ships `quarkus-oidc`, which is the natural first extension to
reach for on this stack — but it's built to secure *the Quarkus
application itself*: it owns its own session/state cookies
(`q_session`, `q_auth_...`), its own redirect handling, and its own
`SecurityIdentity` model. Bending it to produce exactly this repo's cookie
names, CSRF scheme, and DB-backed session store (rather than
`quarkus-oidc`'s own encrypted-cookie or token-relay state) would mean
fighting the extension's own opinions at every step, for a worse result
than just implementing the flow directly — which is what `backend-go`
(`golang.org/x/oauth2` + `coreos/go-oidc`) and `backend-rust`
(`openidconnect`) already do.

## Decision

- `app.nanotes.backend.auth.OidcClient` implements discovery, the
  authorization URL (with S256 PKCE), the code-for-token exchange, and ID
  token verification itself:
  - Discovery (`GET {issuer}/.well-known/openid-configuration`) and the
    token exchange (`POST` to `token_endpoint`) use plain
    `java.net.http.HttpClient` + Jackson — there's no meaningful
    OIDC-specific behavior in either beyond "fetch/post JSON", so a
    dedicated OAuth2 client library adds a dependency without adding
    correctness.
  - ID token signature verification uses `com.nimbusds:nimbus-jose-jwt`
    (`RemoteJWKSet` + `DefaultJWTProcessor`), which Quarkus itself already
    depends on transitively for `quarkus-oidc`/`smallrye-jwt` — it's a
    proven, actively maintained library for exactly this one job (JWS
    verification against a provider's published JWKS), without pulling in
    a full OIDC framework's session/redirect opinions.
  - Accepts a fixed set of common JWS algorithms (RS256/RS384/RS512,
    PS256, ES256/ES384/ES512) rather than hardcoding one, so "any
    standards-compliant provider" (ADR 0004) isn't narrowed to
    RSA-signing providers only.
- Sessions, CSRF tokens, and in-flight OIDC `state`/PKCE `code_verifier`
  are stored in the same SQLite database as everything else
  (`app.nanotes.backend.auth.SessionStore`), the same choice
  `backend-go`/`backend-rust` make and for the same reason (survives a
  restart, no second stateful dependency) — see the root ADR 0004 and
  `backend-go/docs/decisions/0004-sessions-in-sqlite.md` for the fuller
  reasoning, which applies here unchanged.
- Cookies are written by hand (`app.nanotes.backend.web.SessionCookies`)
  with the exact `session`/`csrf_token` names, `HttpOnly`/`Secure`/
  `SameSite=Lax` attributes, and `COOKIE_DOMAIN` handling ADR 0005
  requires — not through `quarkus-oidc`'s cookie machinery.

## Consequences

- No `quarkus-oidc` dependency; instead `com.nimbusds:nimbus-jose-jwt` and
  `org.xerial:sqlite-jdbc` (see ADR 0001) are the two non-Quarkus-BOM
  dependencies this implementation adds.
- The login/callback/logout/me endpoints, session semantics, and CSRF
  behavior are structurally close to `backend-go`'s `internal/auth` +
  `internal/httpapi/auth_handlers.go`, which makes cross-referencing the
  two implementations straightforward — deliberate, since the whole point
  of this repo is comparing implementations of the *same* contract.
- A future contributor adding a fourth OIDC-based backend has two proven
  patterns to choose from: a full OAuth2/OIDC client library
  (`backend-go`, `backend-rust`) or this hand-rolled HTTP+JWT-verification
  approach — both satisfy ADR 0004 equally.
