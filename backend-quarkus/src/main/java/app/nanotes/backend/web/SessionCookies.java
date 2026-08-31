package app.nanotes.backend.web;

/** Builds the Set-Cookie header values for the session/CSRF cookie pair (ADR 0004, ADR 0005). */
final class SessionCookies {

    private SessionCookies() {}

    static String set(String name, String value, String domain, boolean secure, boolean httpOnly, long maxAgeSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value).append("; Path=/");
        if (domain != null && !domain.isEmpty()) {
            sb.append("; Domain=").append(domain);
        }
        sb.append("; Max-Age=").append(maxAgeSeconds);
        sb.append("; SameSite=Lax");
        if (secure) {
            sb.append("; Secure");
        }
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        return sb.toString();
    }

    static String clear(String name, String domain) {
        return set(name, "", domain, false, false, 0);
    }
}
