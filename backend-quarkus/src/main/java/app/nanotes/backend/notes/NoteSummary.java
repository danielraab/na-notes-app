package app.nanotes.backend.notes;

import java.time.Instant;

public record NoteSummary(
        String id,
        String title,
        String contentMarkdown,
        String ownerId,
        Permission myPermission,
        boolean isPublic,
        Instant updatedAt) {}
