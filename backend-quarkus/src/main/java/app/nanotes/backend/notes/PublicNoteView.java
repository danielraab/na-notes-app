package app.nanotes.backend.notes;

import java.time.Instant;

public record PublicNoteView(String title, String contentMarkdown, Instant updatedAt) {}
