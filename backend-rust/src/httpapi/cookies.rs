//! Cookie and header names are part of the cross-implementation contract
//! (ADR 0005) — every backend must use these exact names so a frontend
//! implementation never needs backend-specific logic.

pub const SESSION_COOKIE_NAME: &str = "session";
pub const CSRF_COOKIE_NAME: &str = "csrf_token";
pub const CSRF_HEADER_NAME: &str = "X-CSRF-Token";

/// Builds a `Set-Cookie` header value. Token values here are always
/// URL-safe base64 (see `randtoken`), so no escaping is needed.
pub struct CookieAttrs {
    pub http_only: bool,
    pub secure: bool,
    pub max_age: Option<i64>,
}

pub fn set_cookie(name: &str, value: &str, domain: Option<&str>, attrs: CookieAttrs) -> String {
    let mut out = format!("{name}={value}; Path=/");
    if let Some(domain) = domain {
        out.push_str(&format!("; Domain={domain}"));
    }
    if attrs.http_only {
        out.push_str("; HttpOnly");
    }
    if attrs.secure {
        out.push_str("; Secure");
    }
    out.push_str("; SameSite=Lax");
    if let Some(max_age) = attrs.max_age {
        out.push_str(&format!("; Max-Age={max_age}"));
    }
    out
}

pub fn clear_cookie(name: &str, domain: Option<&str>) -> String {
    let mut out = format!("{name}=; Path=/");
    if let Some(domain) = domain {
        out.push_str(&format!("; Domain={domain}"));
    }
    out.push_str("; Max-Age=0");
    out
}

/// Reads a cookie's value out of the request's `Cookie` header.
pub fn read_cookie(headers: &axum::http::HeaderMap, name: &str) -> Option<String> {
    let raw = headers.get(axum::http::header::COOKIE)?.to_str().ok()?;
    for part in raw.split(';') {
        let part = part.trim();
        if let Some((k, v)) = part.split_once('=') {
            if k == name {
                return Some(v.to_string());
            }
        }
    }
    None
}
