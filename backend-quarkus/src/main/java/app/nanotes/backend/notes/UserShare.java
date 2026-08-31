package app.nanotes.backend.notes;

import java.time.Instant;

public record UserShare(String userId, String displayName, String avatarUrl, Permission permission, Instant createdAt) {}
