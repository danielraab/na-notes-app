package app.nanotes.backend.auth;

import app.nanotes.backend.apperr.NotFoundException;
import app.nanotes.backend.db.Database;
import app.nanotes.backend.db.Timestamps;
import app.nanotes.backend.randtoken.RandToken;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Server-side sessions and in-flight OIDC login state (ADR 0004), stored in the same SQLite database as everything else. */
@ApplicationScoped
public class SessionStore {

    private static final int SESSION_ID_BYTES = 32;
    private static final int CSRF_TOKEN_BYTES = 32;
    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final Duration OIDC_REQUEST_TTL = Duration.ofMinutes(10);
    private static final int OIDC_STATE_BYTES = 24;
    private static final int CODE_VERIFIER_BYTES = 32;

    private final Database db;

    public SessionStore(Database db) {
        this.db = db;
    }

    public Session createSession(String userId) {
        String id = RandToken.generate(SESSION_ID_BYTES);
        String csrf = RandToken.generate(CSRF_TOKEN_BYTES);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(SESSION_TTL);
        db.update(
                "INSERT INTO sessions (id, user_id, csrf_token, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
                id, userId, csrf, Timestamps.format(expiresAt), Timestamps.format(now));
        return new Session(id, userId, csrf, expiresAt);
    }

    public Optional<Session> getSession(String id) {
        Optional<Session> session = db.queryOne(
                "SELECT id, user_id, csrf_token, expires_at FROM sessions WHERE id = ?",
                rs -> new Session(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("csrf_token"),
                        Timestamps.parse(rs.getString("expires_at"))),
                id);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(session.get().expiresAt())) {
            deleteSession(id);
            return Optional.empty();
        }
        return session;
    }

    public void deleteSession(String id) {
        db.update("DELETE FROM sessions WHERE id = ?", id);
    }

    /**
     * Starts a login attempt. Also opportunistically clears expired
     * requests, since they're otherwise never cleaned up (abandoned logins
     * are the only source of them, and volume is low).
     */
    public OidcRequest createOidcRequest(String redirectTo) {
        db.update("DELETE FROM oidc_requests WHERE expires_at < ?", Timestamps.now());

        String state = RandToken.generate(OIDC_STATE_BYTES);
        String verifier = RandToken.generate(CODE_VERIFIER_BYTES);
        Instant expiresAt = Instant.now().plus(OIDC_REQUEST_TTL);
        db.update(
                "INSERT INTO oidc_requests (state, code_verifier, redirect_to, expires_at) VALUES (?, ?, ?, ?)",
                state, verifier, redirectTo, Timestamps.format(expiresAt));
        return new OidcRequest(state, verifier, redirectTo);
    }

    /** Looks up and deletes the request in one step: a state value must only ever be usable once. */
    public OidcRequest consumeOidcRequest(String state) {
        record Row(String codeVerifier, String redirectTo, Instant expiresAt) {}
        Row row = db.queryOne(
                        "SELECT code_verifier, redirect_to, expires_at FROM oidc_requests WHERE state = ?",
                        rs -> new Row(rs.getString("code_verifier"), rs.getString("redirect_to"), Timestamps.parse(rs.getString("expires_at"))),
                        state)
                .orElseThrow(NotFoundException::new);
        db.update("DELETE FROM oidc_requests WHERE state = ?", state);
        if (Instant.now().isAfter(row.expiresAt())) {
            throw new NotFoundException();
        }
        return new OidcRequest(state, row.codeVerifier(), row.redirectTo());
    }
}
