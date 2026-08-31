package app.nanotes.backend.web.dto;

import java.util.List;

public record NoteInputDto(String title, String contentMarkdown, List<String> mentionedUserIds) {}
