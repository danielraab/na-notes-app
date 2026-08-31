package app.nanotes.backend.web

import app.nanotes.backend.apperr.UnauthorizedException
import jakarta.enterprise.context.RequestScoped

/**
 * Holds the session resolved from the request's cookie (if any), populated
 * once per request by [SessionResolutionFilter]. Mirrors backend-go's
 * context values set by sessionContextMiddleware.
 *
 * Exposed as accessor functions rather than `var` properties: this class is
 * `@RequestScoped`, so Quarkus's CDI container generates an open subclass
 * proxy for it (see the `allOpen` config in build.gradle.kts) — an open
 * `var` with a `private set` is invalid Kotlin, since a proxy would need to
 * override the setter too.
 */
@RequestScoped
class CurrentSession {
    private var sessionIdValue: String? = null
    private var userIdValue: String? = null
    private var csrfTokenValue: String? = null

    fun set(sessionId: String, userId: String, csrfToken: String) {
        sessionIdValue = sessionId
        userIdValue = userId
        csrfTokenValue = csrfToken
    }

    /** The raw session cookie value, or null for an anonymous caller. */
    fun sessionId(): String? = sessionIdValue

    /** The caller's user ID, or null for an anonymous caller. */
    fun userId(): String? = userIdValue

    fun csrfToken(): String? = csrfTokenValue

    val isAuthenticated: Boolean
        get() = userIdValue != null

    /** Returns the caller's user ID, or throws [UnauthorizedException] for an anonymous caller. */
    fun requireUserId(): String = userIdValue ?: throw UnauthorizedException()
}
