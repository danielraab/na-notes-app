package app.nanotes.backend.web.dto;

import java.util.List;

public record NotePageDto(List<NoteSummaryDto> items, String nextCursor) {}
