package app.nanotes.backend.notes;

import java.time.Instant;

public record Note(
        String id,
        String ownerId,
        String title,
        String contentMarkdown,
        int version,
        boolean isPublic,
        Instant createdAt,
        Instant updatedAt,
        Permission myPermission) {

    public Note withMyPermission(Permission p) {
        return new Note(id, ownerId, title, contentMarkdown, version, isPublic, createdAt, updatedAt, p);
    }
}
