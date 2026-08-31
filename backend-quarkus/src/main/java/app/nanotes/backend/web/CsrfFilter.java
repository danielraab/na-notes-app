package app.nanotes.backend.web;

import jakarta.annotation.Priority;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Enforces the double-submit cookie pattern (ADR 0005) on state-changing
 * requests. Requests without a resolved session are let through unchecked
 * here — resources that require auth reject them with 401 themselves,
 * which is the more useful error for a caller that was never going to be
 * authorized anyway. Mirrors backend-go's csrfMiddleware.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
public class CsrfFilter implements ContainerRequestFilter {

    private static final Set<String> STATE_CHANGING =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    private final CurrentSession currentSession;

    public CsrfFilter(CurrentSession currentSession) {
        this.currentSession = currentSession;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!STATE_CHANGING.contains(requestContext.getMethod())) {
            return;
        }
        if (!currentSession.isAuthenticated()) {
            return;
        }
        String expected = currentSession.csrfToken();
        String got = requestContext.getHeaderString(CookieNames.CSRF_HEADER);
        if (got == null || !constantTimeEquals(got, expected)) {
            requestContext.abortWith(ErrorResponses.of(Response.Status.FORBIDDEN, "CSRF_REJECTED", "missing or invalid CSRF token"));
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
