package app.nanotes.backend.web.dto;

import java.time.Instant;

public record NoteSummaryDto(
        String id,
        String title,
        String contentMarkdown,
        String ownerId,
        String myPermission,
        boolean isPublic,
        Instant updatedAt) {}
