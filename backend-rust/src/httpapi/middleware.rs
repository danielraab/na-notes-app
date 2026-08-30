use std::collections::HashSet;
use std::sync::Arc;
use std::time::Instant;

use axum::extract::{FromRequestParts, Request, State};
use axum::http::request::Parts;
use axum::http::{HeaderMap, Method, StatusCode};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};

use super::cookies::{read_cookie, CSRF_HEADER_NAME, SESSION_COOKIE_NAME};
use super::respond::ApiError;
use super::server::Deps;

/// Per-request auth state, resolved once by `session_context_middleware`
/// and read by everything downstream (the CSRF check, and the
/// `RequireAuth`/`OptionalAuth` extractors).
#[derive(Clone, Debug, Default)]
pub struct AuthContext {
    pub user_id: Option<String>,
    pub csrf_token: Option<String>,
}

/// Extracts the current user's ID, rejecting the request with 401 if
/// there isn't one.
pub struct RequireAuth(pub String);

impl<S: Send + Sync> FromRequestParts<S> for RequireAuth {
    type Rejection = ApiError;

    async fn from_request_parts(parts: &mut Parts, _state: &S) -> Result<Self, Self::Rejection> {
        let ctx = parts
            .extensions
            .get::<AuthContext>()
            .cloned()
            .unwrap_or_default();
        match ctx.user_id {
            Some(uid) => Ok(RequireAuth(uid)),
            None => Err(ApiError::unauthenticated()),
        }
    }
}

/// Extracts the current user's ID if there is one, without rejecting
/// anonymous callers — for endpoints with public behavior (dashboard feed,
/// current-user check).
pub struct OptionalAuth(pub Option<String>);

impl<S: Send + Sync> FromRequestParts<S> for OptionalAuth {
    type Rejection = std::convert::Infallible;

    async fn from_request_parts(parts: &mut Parts, _state: &S) -> Result<Self, Self::Rejection> {
        let ctx = parts
            .extensions
            .get::<AuthContext>()
            .cloned()
            .unwrap_or_default();
        Ok(OptionalAuth(ctx.user_id))
    }
}

/// Resolves the session cookie (if any) once per request and stores the
/// result in request extensions, so downstream middleware/handlers never
/// need to touch the session store themselves.
pub async fn session_context_middleware(
    State(deps): State<Arc<Deps>>,
    mut req: Request,
    next: Next,
) -> Response {
    let mut ctx = AuthContext::default();
    if let Some(session_id) = read_cookie(req.headers(), SESSION_COOKIE_NAME) {
        if let Ok(session) = deps.auth_store.get_session(session_id).await {
            ctx.user_id = Some(session.user_id);
            ctx.csrf_token = Some(session.csrf_token);
        }
    }
    req.extensions_mut().insert(ctx);
    next.run(req).await
}

fn is_state_changing(method: &Method) -> bool {
    matches!(
        method,
        &Method::POST | &Method::PUT | &Method::PATCH | &Method::DELETE
    )
}

/// Constant-time comparison, mirroring Go's `crypto/subtle.ConstantTimeCompare`.
fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for (x, y) in a.iter().zip(b.iter()) {
        diff |= x ^ y;
    }
    diff == 0
}

/// Enforces the double-submit cookie pattern (ADR 0005) on state-changing
/// requests. Requests without a session are let through unchecked here —
/// `RequireAuth` rejects them with 401, which is the more useful error for
/// a caller that was never going to be authorized anyway.
pub async fn csrf_middleware(req: Request, next: Next) -> Response {
    if !is_state_changing(req.method()) {
        return next.run(req).await;
    }
    let ctx = req
        .extensions()
        .get::<AuthContext>()
        .cloned()
        .unwrap_or_default();
    let Some(expected) = ctx.csrf_token else {
        return next.run(req).await;
    };
    let got = req
        .headers()
        .get(CSRF_HEADER_NAME)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");
    if got.is_empty() || !constant_time_eq(got.as_bytes(), expected.as_bytes()) {
        return ApiError::new(
            StatusCode::FORBIDDEN,
            "CSRF_REJECTED",
            "missing or invalid CSRF token",
        )
        .into_response();
    }
    next.run(req).await
}

/// Only ever reflects an explicitly allow-listed origin (ADR 0005) — never
/// combines a wildcard origin with credentials.
pub async fn cors_middleware(State(deps): State<Arc<Deps>>, req: Request, next: Next) -> Response {
    let allowed: HashSet<&str> = deps
        .config
        .allowed_origins
        .iter()
        .map(String::as_str)
        .collect();
    let origin = req
        .headers()
        .get(axum::http::header::ORIGIN)
        .and_then(|v| v.to_str().ok())
        .map(str::to_string);
    let is_allowed = origin.as_deref().is_some_and(|o| allowed.contains(o));

    if req.method() == Method::OPTIONS {
        let mut headers = HeaderMap::new();
        if is_allowed {
            add_cors_headers(&mut headers, origin.as_deref().unwrap());
        }
        headers.insert(
            "Access-Control-Allow-Methods",
            "GET, POST, PUT, DELETE, OPTIONS".parse().unwrap(),
        );
        headers.insert(
            "Access-Control-Allow-Headers",
            format!("Content-Type, If-Match, {CSRF_HEADER_NAME}")
                .parse()
                .unwrap(),
        );
        return (StatusCode::NO_CONTENT, headers).into_response();
    }

    let mut res = next.run(req).await;
    if is_allowed {
        add_cors_headers(res.headers_mut(), origin.as_deref().unwrap());
    }
    res
}

fn add_cors_headers(headers: &mut HeaderMap, origin: &str) {
    if let Ok(v) = origin.parse() {
        headers.insert("Access-Control-Allow-Origin", v);
    }
    headers.insert("Access-Control-Allow-Credentials", "true".parse().unwrap());
    headers.insert(axum::http::header::VARY, "Origin".parse().unwrap());
}

pub async fn request_logger(req: Request, next: Next) -> Response {
    let start = Instant::now();
    let method = req.method().clone();
    let path = req.uri().path().to_string();
    let res = next.run(req).await;
    tracing::info!(
        method = %method,
        path = %path,
        status = res.status().as_u16(),
        duration_ms = start.elapsed().as_millis() as u64,
        "request"
    );
    res
}
