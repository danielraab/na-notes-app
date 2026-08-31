package app.nanotes.backend.web

import jakarta.annotation.Priority
import jakarta.ws.rs.HttpMethod
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Enforces the double-submit cookie pattern (ADR 0005) on state-changing
 * requests. Requests without a resolved session are let through unchecked
 * here — resources that require auth reject them with 401 themselves,
 * which is the more useful error for a caller that was never going to be
 * authorized anyway. Mirrors backend-go's csrfMiddleware.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
class CsrfFilter(private val currentSession: CurrentSession) : ContainerRequestFilter {

    override fun filter(requestContext: ContainerRequestContext) {
        if (requestContext.method !in STATE_CHANGING) return
        if (!currentSession.isAuthenticated) return

        // Safe: isAuthenticated (userId != null) and csrfToken are always set together by CurrentSession.set.
        val expected = currentSession.csrfToken()!!
        val got = requestContext.getHeaderString(CookieNames.CSRF_HEADER)
        if (got == null || !constantTimeEquals(got, expected)) {
            requestContext.abortWith(ErrorResponses.of(Response.Status.FORBIDDEN, "CSRF_REJECTED", "missing or invalid CSRF token"))
        }
    }

    companion object {
        private val STATE_CHANGING = setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)

        private fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(StandardCharsets.UTF_8), b.toByteArray(StandardCharsets.UTF_8))
    }
}
