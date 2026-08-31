package app.nanotes.backend.auth;

import java.time.Instant;

public record Session(String id, String userId, String csrfToken, Instant expiresAt) {}
