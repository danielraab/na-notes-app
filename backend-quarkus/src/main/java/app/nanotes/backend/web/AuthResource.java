package app.nanotes.backend.web;

import app.nanotes.backend.apperr.NotFoundException;
import app.nanotes.backend.auth.Claims;
import app.nanotes.backend.auth.OidcClient;
import app.nanotes.backend.auth.OidcException;
import app.nanotes.backend.auth.OidcRequest;
import app.nanotes.backend.auth.Session;
import app.nanotes.backend.auth.SessionStore;
import app.nanotes.backend.config.AppConfig;
import app.nanotes.backend.users.User;
import app.nanotes.backend.users.UserRepository;
import app.nanotes.backend.web.dto.UserDto;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import org.jboss.logging.Logger;

/** Login/logout/current-user endpoints (ADR 0004). */
@Path("/api/auth")
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    private final AppConfig config;
    private final SessionStore sessionStore;
    private final OidcClient oidc;
    private final UserRepository users;
    private final CurrentSession currentSession;

    public AuthResource(AppConfig config, SessionStore sessionStore, OidcClient oidc, UserRepository users, CurrentSession currentSession) {
        this.config = config;
        this.sessionStore = sessionStore;
        this.oidc = oidc;
        this.users = users;
        this.currentSession = currentSession;
    }

    /** Restricts post-login redirects to an in-app path, to avoid the login flow being used as an open redirect. */
    private static boolean isSafeRedirectPath(String p) {
        return p != null && !p.isEmpty() && p.startsWith("/") && !p.startsWith("//");
    }

    @GET
    @Path("/login")
    public Response login(@QueryParam("redirectTo") String redirectTo) {
        String safe = isSafeRedirectPath(redirectTo) ? redirectTo : "/";
        OidcRequest req = sessionStore.createOidcRequest(safe);
        return Response.status(Response.Status.FOUND).header("Location", oidc.authCodeUrl(req.state(), req.codeVerifier())).build();
    }

    @GET
    @Path("/callback")
    public Response callback(@QueryParam("code") String code, @QueryParam("state") String state) {
        if (code == null || code.isEmpty() || state == null || state.isEmpty()) {
            return ErrorResponses.of(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", "missing code or state");
        }

        OidcRequest req;
        try {
            req = sessionStore.consumeOidcRequest(state);
        } catch (NotFoundException e) {
            return ErrorResponses.of(Response.Status.BAD_REQUEST, "INVALID_STATE", "login request expired or was already used");
        }

        Claims claims;
        try {
            claims = oidc.exchange(code, req.codeVerifier());
        } catch (OidcException e) {
            LOG.error("oidc exchange failed", e);
            return ErrorResponses.of(Response.Status.BAD_GATEWAY, "OIDC_EXCHANGE_FAILED", "could not complete login with identity provider");
        }

        User user = users.upsertFromOidc(claims.subject(), claims.email(), claims.displayName(), claims.avatarUrl());
        Session session = sessionStore.createSession(user.id());

        boolean secure = config.secureCookies();
        long maxAge = Duration.between(Instant.now(), session.expiresAt()).getSeconds();

        return Response.status(Response.Status.FOUND)
                .header("Location", config.frontendUrl() + req.redirectTo())
                .header("Set-Cookie", SessionCookies.set(CookieNames.SESSION, session.id(), config.cookieDomain(), secure, true, maxAge))
                // Readable by frontend JS on purpose — it's echoed back as the
                // X-CSRF-Token header, never trusted as an identity credential.
                .header("Set-Cookie", SessionCookies.set(CookieNames.CSRF, session.csrfToken(), config.cookieDomain(), secure, false, maxAge))
                .build();
    }

    @POST
    @Path("/logout")
    public Response logout() {
        currentSession.requireUserId();
        if (currentSession.sessionId() != null) {
            sessionStore.deleteSession(currentSession.sessionId());
        }
        return Response.noContent()
                .header("Set-Cookie", SessionCookies.clear(CookieNames.SESSION, config.cookieDomain()))
                .header("Set-Cookie", SessionCookies.clear(CookieNames.CSRF, config.cookieDomain()))
                .build();
    }

    @GET
    @Path("/me")
    public UserDto me() {
        String userId = currentSession.requireUserId();
        return Dtos.toUserDto(users.getById(userId));
    }
}
