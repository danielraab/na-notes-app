package app.nanotes.backend.auth

import java.time.Instant

/** The subset of ID token / userinfo claims the application needs, independent of which OIDC provider issued them. */
data class Claims(val subject: String, val email: String, val displayName: String, val avatarUrl: String?)

data class Session(val id: String, val userId: String, val csrfToken: String, val expiresAt: Instant)

/**
 * Server-side record of an in-flight login, keyed by the OAuth `state`. The
 * PKCE codeVerifier must never be exposed to the browser, so it's kept here
 * rather than in a client-readable cookie.
 */
data class OidcRequest(val state: String, val codeVerifier: String, val redirectTo: String)

/** The OIDC provider could not be reached, or rejected/mismatched a login attempt. */
class OidcException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
