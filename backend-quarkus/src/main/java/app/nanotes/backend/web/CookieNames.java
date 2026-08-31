package app.nanotes.backend.web;

/**
 * Cookie and header names are part of the cross-implementation contract
 * (ADR 0005) — every backend must use these exact names so a frontend
 * implementation never needs backend-specific logic.
 */
public final class CookieNames {
    public static final String SESSION = "session";
    public static final String CSRF = "csrf_token";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private CookieNames() {}
}
