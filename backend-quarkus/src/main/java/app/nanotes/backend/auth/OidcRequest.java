package app.nanotes.backend.auth;

/**
 * Server-side record of an in-flight login, keyed by the OAuth {@code state}.
 * The PKCE {@code codeVerifier} must never be exposed to the browser, so
 * it's kept here rather than in a client-readable cookie.
 */
public record OidcRequest(String state, String codeVerifier, String redirectTo) {}
