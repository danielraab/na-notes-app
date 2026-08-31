package app.nanotes.backend.web;

import app.nanotes.backend.apperr.UnauthorizedException;
import jakarta.enterprise.context.RequestScoped;

/**
 * Holds the session resolved from the request's cookie (if any), populated
 * once per request by {@link SessionResolutionFilter}. Mirrors backend-go's
 * context values set by sessionContextMiddleware.
 */
@RequestScoped
public class CurrentSession {

    private String sessionId;
    private String userId;
    private String csrfToken;

    void set(String sessionId, String userId, String csrfToken) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.csrfToken = csrfToken;
    }

    /** The raw session cookie value, or null for an anonymous caller. */
    public String sessionId() {
        return sessionId;
    }

    /** The caller's user ID, or null for an anonymous caller. */
    public String userId() {
        return userId;
    }

    public String csrfToken() {
        return csrfToken;
    }

    public boolean isAuthenticated() {
        return userId != null;
    }

    /** Returns the caller's user ID, or throws {@link UnauthorizedException} for an anonymous caller. */
    public String requireUserId() {
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return userId;
    }
}
