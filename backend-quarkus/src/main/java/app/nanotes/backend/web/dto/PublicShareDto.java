package app.nanotes.backend.web.dto;

import java.time.Instant;

public record PublicShareDto(String token, String url, Instant createdAt) {}
