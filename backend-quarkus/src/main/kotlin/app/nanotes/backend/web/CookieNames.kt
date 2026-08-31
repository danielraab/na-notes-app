package app.nanotes.backend.web

/**
 * Cookie and header names are part of the cross-implementation contract
 * (ADR 0005) — every backend must use these exact names so a frontend
 * implementation never needs backend-specific logic.
 */
object CookieNames {
    const val SESSION = "session"
    const val CSRF = "csrf_token"
    const val CSRF_HEADER = "X-CSRF-Token"
}
