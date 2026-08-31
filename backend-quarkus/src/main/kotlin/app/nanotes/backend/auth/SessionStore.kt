package app.nanotes.backend.auth

import app.nanotes.backend.apperr.NotFoundException
import app.nanotes.backend.db.Database
import app.nanotes.backend.db.Timestamps
import app.nanotes.backend.randtoken.RandToken
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.time.Instant

/** Server-side sessions and in-flight OIDC login state (ADR 0004), stored in the same SQLite database as everything else. */
@ApplicationScoped
class SessionStore(private val db: Database) {

    companion object {
        private const val SESSION_ID_BYTES = 32
        private const val CSRF_TOKEN_BYTES = 32
        private val SESSION_TTL: Duration = Duration.ofDays(7)
        private val OIDC_REQUEST_TTL: Duration = Duration.ofMinutes(10)
        private const val OIDC_STATE_BYTES = 24
        private const val CODE_VERIFIER_BYTES = 32
    }

    fun createSession(userId: String): Session {
        val id = RandToken.generate(SESSION_ID_BYTES)
        val csrf = RandToken.generate(CSRF_TOKEN_BYTES)
        val now = Instant.now()
        val expiresAt = now.plus(SESSION_TTL)
        db.update(
            "INSERT INTO sessions (id, user_id, csrf_token, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
            id, userId, csrf, Timestamps.format(expiresAt), Timestamps.format(now),
        )
        return Session(id, userId, csrf, expiresAt)
    }

    fun getSession(id: String): Session? {
        val session = db.queryOne(
            "SELECT id, user_id, csrf_token, expires_at FROM sessions WHERE id = ?",
            { rs ->
                Session(rs.getString("id"), rs.getString("user_id"), rs.getString("csrf_token"), Timestamps.parse(rs.getString("expires_at")))
            },
            id,
        ) ?: return null
        if (Instant.now().isAfter(session.expiresAt)) {
            deleteSession(id)
            return null
        }
        return session
    }

    fun deleteSession(id: String) {
        db.update("DELETE FROM sessions WHERE id = ?", id)
    }

    /**
     * Starts a login attempt. Also opportunistically clears expired
     * requests, since they're otherwise never cleaned up (abandoned logins
     * are the only source of them, and volume is low).
     */
    fun createOidcRequest(redirectTo: String): OidcRequest {
        db.update("DELETE FROM oidc_requests WHERE expires_at < ?", Timestamps.now())

        val state = RandToken.generate(OIDC_STATE_BYTES)
        val verifier = RandToken.generate(CODE_VERIFIER_BYTES)
        val expiresAt = Instant.now().plus(OIDC_REQUEST_TTL)
        db.update(
            "INSERT INTO oidc_requests (state, code_verifier, redirect_to, expires_at) VALUES (?, ?, ?, ?)",
            state, verifier, redirectTo, Timestamps.format(expiresAt),
        )
        return OidcRequest(state, verifier, redirectTo)
    }

    /** Looks up and deletes the request in one step: a state value must only ever be usable once. */
    fun consumeOidcRequest(state: String): OidcRequest {
        data class Row(val codeVerifier: String, val redirectTo: String, val expiresAt: Instant)

        val row = db.queryOne(
            "SELECT code_verifier, redirect_to, expires_at FROM oidc_requests WHERE state = ?",
            { rs -> Row(rs.getString("code_verifier"), rs.getString("redirect_to"), Timestamps.parse(rs.getString("expires_at"))) },
            state,
        ) ?: throw NotFoundException()

        db.update("DELETE FROM oidc_requests WHERE state = ?", state)
        if (Instant.now().isAfter(row.expiresAt)) {
            throw NotFoundException()
        }
        return OidcRequest(state, row.codeVerifier, row.redirectTo)
    }
}
