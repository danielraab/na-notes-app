package app.nanotes.backend.web

/** Builds the Set-Cookie header values for the session/CSRF cookie pair (ADR 0004, ADR 0005). */
internal object SessionCookies {

    fun set(name: String, value: String, domain: String?, secure: Boolean, httpOnly: Boolean, maxAgeSeconds: Long): String {
        val sb = StringBuilder()
        sb.append(name).append('=').append(value).append("; Path=/")
        if (!domain.isNullOrEmpty()) {
            sb.append("; Domain=").append(domain)
        }
        sb.append("; Max-Age=").append(maxAgeSeconds)
        sb.append("; SameSite=Lax")
        if (secure) sb.append("; Secure")
        if (httpOnly) sb.append("; HttpOnly")
        return sb.toString()
    }

    fun clear(name: String, domain: String?): String = set(name, "", domain, secure = false, httpOnly = false, maxAgeSeconds = 0)
}
