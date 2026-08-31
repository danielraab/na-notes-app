package app.nanotes.backend.auth;

/** The subset of ID token / userinfo claims the application needs, independent of which OIDC provider issued them. */
public record Claims(String subject, String email, String displayName, String avatarUrl) {}
