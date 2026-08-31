# 4. OIDC built on plain httpx + joserfc, not a full OIDC client library

## Status

Accepted

## Context

`backend-go` implements the OIDC Authorization Code + PKCE flow (ADR 0004)
with two focused libraries: `coreos/go-oidc` for provider discovery and ID
token verification, and `golang.org/x/oauth2` for the code exchange itself
(`backend-go/internal/auth/oidc.go`). Python's ecosystem has several
higher-level OIDC/OAuth client frameworks that bundle discovery, PKCE, and
token verification behind a single integration object (commonly built
around session-based state storage), which doesn't fit here: this project
keeps in-flight login state (`state` + PKCE `code_verifier`) server-side in
SQLite (`app/auth/store.py`, see ADR 0003 in this folder), not in a
client-readable cookie-backed session, so a framework integration that
assumes the latter would have to be worked around rather than used as
intended.

## Decision

`app/auth/oidc.py` implements the same small protocol surface `backend-go`
does, directly:

- **Discovery**: one `httpx.get` of `{issuer}/.well-known/openid-configuration`.
- **Authorization URL**: built by hand with `urllib.parse.urlencode`; the
  PKCE `code_challenge` is `base64url(sha256(code_verifier))`, computed
  with the standard library (`hashlib`, `base64`) — the same three-line
  operation as `backend-go`'s `CodeChallenge`.
- **Code exchange**: one `httpx.post` to the token endpoint with the
  standard `authorization_code` + PKCE `code_verifier` body.
- **ID token verification**: `joserfc` (`jwt.decode` + `JWTClaimsRegistry`)
  validates the signature against the provider's JWKS (fetched fresh per
  exchange — logins are infrequent enough that this project doesn't need
  key caching/rotation logic) and checks `iss`/`aud`/expiry.

No provider-specific code exists anywhere in this path (ADR 0004's
requirement) — only the issuer URL and client credentials are configured.

## Consequences

- No dependency on a framework that assumes a particular session storage
  model; `app/auth/store.py` stays the single place login state lives.
- The whole flow is four HTTP-shaped operations and one JWT decode, kept
  in one file that's easy to audit against ADR 0004/0009, the same
  legibility goal `backend-rust`'s hand-rolled cookie/CORS code cites in
  `backend-rust/docs/decisions/0001-axum-web-framework.md`.
- `joserfc` is used instead of `authlib`'s bundled JOSE module: at the
  dependency versions this project resolved against, `authlib`'s
  `authlib.jose` and `authlib.integrations.httpx_client` were both flagged
  deprecated in favor of `joserfc` and a separate `httpx2` package
  respectively; depending on `joserfc` directly (a focused, actively
  maintained JOSE library) plus plain `httpx` avoids building on APIs a
  dependency itself is moving away from.
