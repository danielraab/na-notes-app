package app.nanotes.backend.web;

import app.nanotes.backend.auth.Session;
import app.nanotes.backend.auth.SessionStore;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;
import java.util.Optional;

/**
 * Resolves the session cookie (if any) once per request into
 * {@link CurrentSession}, so downstream filters/resources never touch the
 * session store themselves. Mirrors backend-go's sessionContextMiddleware.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class SessionResolutionFilter implements ContainerRequestFilter {

    private final SessionStore sessionStore;
    private final CurrentSession currentSession;

    public SessionResolutionFilter(SessionStore sessionStore, CurrentSession currentSession) {
        this.sessionStore = sessionStore;
        this.currentSession = currentSession;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Cookie cookie = requestContext.getCookies().get(CookieNames.SESSION);
        if (cookie == null) {
            return;
        }
        Optional<Session> session = sessionStore.getSession(cookie.getValue());
        session.ifPresent(s -> currentSession.set(s.id(), s.userId(), s.csrfToken()));
    }
}
