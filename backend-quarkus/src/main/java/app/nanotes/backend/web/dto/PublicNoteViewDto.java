package app.nanotes.backend.web.dto;

import java.time.Instant;

public record PublicNoteViewDto(String title, String contentMarkdown, Instant updatedAt) {}
