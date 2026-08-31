package app.nanotes.backend.web.dto;

import java.time.Instant;

public record NoteDto(
        String id,
        String title,
        String contentMarkdown,
        String ownerId,
        int version,
        String myPermission,
        boolean isPublic,
        Instant createdAt,
        Instant updatedAt) {}
