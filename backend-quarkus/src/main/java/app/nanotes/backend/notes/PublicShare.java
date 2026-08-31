package app.nanotes.backend.notes;

import java.time.Instant;

public record PublicShare(String token, Instant createdAt) {}
