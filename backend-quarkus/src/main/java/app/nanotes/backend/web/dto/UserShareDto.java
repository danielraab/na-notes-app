package app.nanotes.backend.web.dto;

import java.time.Instant;

public record UserShareDto(UserSummaryDto user, String permission, Instant createdAt) {}
