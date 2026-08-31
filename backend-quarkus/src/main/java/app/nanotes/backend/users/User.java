package app.nanotes.backend.users;

import java.time.Instant;

public record User(String id, String email, String displayName, String avatarUrl, Instant createdAt) {}
