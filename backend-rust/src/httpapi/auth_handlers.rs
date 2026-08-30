use std::sync::Arc;

use axum::body::Body;
use axum::extract::{Query, State};
use axum::http::{header, HeaderMap, StatusCode};
use axum::response::Response;
use axum::Json;
use serde::Deserialize;

use crate::apperr::AppError;

use super::cookies::{
    clear_cookie, read_cookie, set_cookie, CookieAttrs, CSRF_COOKIE_NAME, SESSION_COOKIE_NAME,
};
use super::dto::to_user_dto;
use super::middleware::{OptionalAuth, RequireAuth};
use super::respond::ApiError;
use super::server::Deps;

/// Restricts post-login redirects to an in-app path, to avoid the login
/// flow being used as an open redirect.
fn is_safe_redirect_path(p: &str) -> bool {
    !p.is_empty() && p.starts_with('/') && !p.starts_with("//")
}

#[derive(Deserialize)]
pub struct LoginQuery {
    #[serde(default, rename = "redirectTo")]
    redirect_to: String,
}

pub async fn handle_login(
    State(deps): State<Arc<Deps>>,
    Query(q): Query<LoginQuery>,
) -> Result<Response, ApiError> {
    let redirect_to = if is_safe_redirect_path(&q.redirect_to) {
        q.redirect_to
    } else {
        "/".to_string()
    };

    let req = deps.auth_store.create_oidc_request(redirect_to).await?;
    let url = deps.oidc.auth_code_url(req.state, &req.code_verifier);

    Ok(redirect_found(&url))
}

#[derive(Deserialize)]
pub struct CallbackQuery {
    #[serde(default)]
    code: String,
    #[serde(default)]
    state: String,
}

pub async fn handle_callback(
    State(deps): State<Arc<Deps>>,
    Query(q): Query<CallbackQuery>,
) -> Result<Response, ApiError> {
    if q.code.is_empty() || q.state.is_empty() {
        return Err(ApiError::new(
            StatusCode::BAD_REQUEST,
            "VALIDATION_ERROR",
            "missing code or state",
        ));
    }

    let req = deps
        .auth_store
        .consume_oidc_request(q.state)
        .await
        .map_err(|_| {
            ApiError::new(
                StatusCode::BAD_REQUEST,
                "INVALID_STATE",
                "login request expired or was already used",
            )
        })?;

    let claims = deps
        .oidc
        .exchange(q.code, req.code_verifier)
        .await
        .map_err(|e| {
            tracing::error!(error = %e, "oidc exchange failed");
            ApiError::new(
                StatusCode::BAD_GATEWAY,
                "OIDC_EXCHANGE_FAILED",
                "could not complete login with identity provider",
            )
        })?;

    let user = deps
        .users
        .upsert_from_oidc(
            claims.subject,
            claims.email,
            claims.display_name,
            claims.avatar_url,
        )
        .await?;
    let session = deps.auth_store.create_session(user.id).await?;

    let secure = deps.config.public_base_url.starts_with("https://");
    let max_age = (session.expires_at - chrono::Utc::now()).num_seconds();
    let domain = deps.config.cookie_domain.as_deref();

    let session_cookie = set_cookie(
        SESSION_COOKIE_NAME,
        &session.id,
        domain,
        CookieAttrs {
            http_only: true,
            secure,
            max_age: Some(max_age),
        },
    );
    // Readable by frontend JS on purpose — it's echoed back as the
    // X-CSRF-Token header, never trusted as an identity credential itself.
    let csrf_cookie = set_cookie(
        CSRF_COOKIE_NAME,
        &session.csrf_token,
        domain,
        CookieAttrs {
            http_only: false,
            secure,
            max_age: Some(max_age),
        },
    );

    let location = format!("{}{}", deps.config.frontend_url, req.redirect_to);
    let response = Response::builder()
        .status(StatusCode::FOUND)
        .header(header::LOCATION, location)
        .header(header::SET_COOKIE, session_cookie)
        .header(header::SET_COOKIE, csrf_cookie)
        .body(Body::empty())
        .expect("valid response");

    Ok(response)
}

pub async fn handle_logout(
    State(deps): State<Arc<Deps>>,
    _auth: RequireAuth,
    headers: HeaderMap,
) -> Result<Response, ApiError> {
    if let Some(session_id) = read_cookie(&headers, SESSION_COOKIE_NAME) {
        let _ = deps.auth_store.delete_session(session_id).await;
    }
    let domain = deps.config.cookie_domain.as_deref();
    let response = Response::builder()
        .status(StatusCode::NO_CONTENT)
        .header(
            header::SET_COOKIE,
            clear_cookie(SESSION_COOKIE_NAME, domain),
        )
        .header(header::SET_COOKIE, clear_cookie(CSRF_COOKIE_NAME, domain))
        .body(Body::empty())
        .expect("valid response");
    Ok(response)
}

pub async fn handle_me(
    State(deps): State<Arc<Deps>>,
    OptionalAuth(user_id): OptionalAuth,
) -> Result<Json<super::dto::UserDto>, ApiError> {
    let Some(user_id) = user_id else {
        return Err(ApiError::unauthenticated());
    };
    let user = deps
        .users
        .get_by_id(user_id)
        .await
        .map_err(|_| AppError::NotFound)?;
    Ok(Json(to_user_dto(user)))
}

fn redirect_found(url: &str) -> Response {
    Response::builder()
        .status(StatusCode::FOUND)
        .header(header::LOCATION, url)
        .body(Body::empty())
        .expect("valid response")
}
