package app.nanotes.backend.web

import app.nanotes.backend.auth.SessionStore
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider

/**
 * Resolves the session cookie (if any) once per request into
 * [CurrentSession], so downstream filters/resources never touch the
 * session store themselves. Mirrors backend-go's sessionContextMiddleware.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
class SessionResolutionFilter(
    private val sessionStore: SessionStore,
    private val currentSession: CurrentSession,
) : ContainerRequestFilter {

    override fun filter(requestContext: ContainerRequestContext) {
        val cookie = requestContext.cookies[CookieNames.SESSION] ?: return
        val session = sessionStore.getSession(cookie.value) ?: return
        currentSession.set(session.id, session.userId, session.csrfToken)
    }
}
