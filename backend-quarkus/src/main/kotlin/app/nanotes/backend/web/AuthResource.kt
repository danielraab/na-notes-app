package app.nanotes.backend.web

import app.nanotes.backend.apperr.NotFoundException
import app.nanotes.backend.auth.OidcClient
import app.nanotes.backend.auth.OidcException
import app.nanotes.backend.auth.SessionStore
import app.nanotes.backend.config.AppConfig
import app.nanotes.backend.users.UserRepository
import app.nanotes.backend.web.dto.UserDto
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response
import java.time.Duration
import java.time.Instant
import org.jboss.logging.Logger

/** Login/logout/current-user endpoints (ADR 0004). */
@Path("/api/auth")
class AuthResource(
    private val config: AppConfig,
    private val sessionStore: SessionStore,
    private val oidc: OidcClient,
    private val users: UserRepository,
    private val currentSession: CurrentSession,
) {
    @GET
    @Path("/login")
    fun login(@QueryParam("redirectTo") redirectTo: String?): Response {
        val safe = if (isSafeRedirectPath(redirectTo)) redirectTo!! else "/"
        val req = sessionStore.createOidcRequest(safe)
        return Response.status(Response.Status.FOUND)
            .header("Location", oidc.authCodeUrl(req.state, req.codeVerifier))
            .build()
    }

    @GET
    @Path("/callback")
    fun callback(@QueryParam("code") code: String?, @QueryParam("state") state: String?): Response {
        if (code.isNullOrEmpty() || state.isNullOrEmpty()) {
            return ErrorResponses.of(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", "missing code or state")
        }

        val req = try {
            sessionStore.consumeOidcRequest(state)
        } catch (e: NotFoundException) {
            return ErrorResponses.of(Response.Status.BAD_REQUEST, "INVALID_STATE", "login request expired or was already used")
        }

        val claims = try {
            oidc.exchange(code, req.codeVerifier)
        } catch (e: OidcException) {
            LOG.error("oidc exchange failed", e)
            return ErrorResponses.of(Response.Status.BAD_GATEWAY, "OIDC_EXCHANGE_FAILED", "could not complete login with identity provider")
        }

        val user = users.upsertFromOidc(claims.subject, claims.email, claims.displayName, claims.avatarUrl)
        val session = sessionStore.createSession(user.id)

        val secure = config.secureCookies()
        val maxAge = Duration.between(Instant.now(), session.expiresAt).seconds

        return Response.status(Response.Status.FOUND)
            .header("Location", config.frontendUrl + req.redirectTo)
            .header("Set-Cookie", SessionCookies.set(CookieNames.SESSION, session.id, config.cookieDomain, secure, httpOnly = true, maxAge))
            // Readable by frontend JS on purpose — it's echoed back as the
            // X-CSRF-Token header, never trusted as an identity credential.
            .header("Set-Cookie", SessionCookies.set(CookieNames.CSRF, session.csrfToken, config.cookieDomain, secure, httpOnly = false, maxAge))
            .build()
    }

    @POST
    @Path("/logout")
    fun logout(): Response {
        currentSession.requireUserId()
        currentSession.sessionId()?.let { sessionStore.deleteSession(it) }
        return Response.noContent()
            .header("Set-Cookie", SessionCookies.clear(CookieNames.SESSION, config.cookieDomain))
            .header("Set-Cookie", SessionCookies.clear(CookieNames.CSRF, config.cookieDomain))
            .build()
    }

    @GET
    @Path("/me")
    fun me(): UserDto {
        val userId = currentSession.requireUserId()
        return Dtos.toUserDto(users.getById(userId))
    }

    companion object {
        private val LOG: Logger = Logger.getLogger(AuthResource::class.java)

        /** Restricts post-login redirects to an in-app path, to avoid the login flow being used as an open redirect. */
        private fun isSafeRedirectPath(p: String?): Boolean =
            !p.isNullOrEmpty() && p.startsWith("/") && !p.startsWith("//")
    }
}
