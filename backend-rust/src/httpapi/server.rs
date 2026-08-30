//! Wires the REST API defined in /openapi/openapi.yaml: routing,
//! CORS/CSRF/session middleware, and request/response mapping. Domain
//! logic itself lives in `notes`, `users`, `auth`.

use std::sync::Arc;

use axum::http::StatusCode;
use axum::middleware;
use axum::routing::{delete, get, post};
use axum::Router;

use crate::auth;
use crate::config::Config;
use crate::notes;
use crate::users;

use super::{
    auth_handlers, middleware as own_middleware, notes_handlers, public_handlers, sharing_handlers,
    users_handlers,
};

/// Everything the HTTP layer needs. Has no behavior of its own beyond
/// being a container passed to route registration.
pub struct Deps {
    pub config: Config,
    pub auth_store: auth::Store,
    pub oidc: auth::Oidc,
    pub users: users::Repository,
    pub notes: notes::Service,
}

pub fn build_router(deps: Arc<Deps>) -> Router {
    let router = Router::new()
        .route("/healthz", get(|| async { StatusCode::OK }))
        .route("/api/auth/login", get(auth_handlers::handle_login))
        .route("/api/auth/callback", get(auth_handlers::handle_callback))
        .route("/api/auth/logout", post(auth_handlers::handle_logout))
        .route("/api/auth/me", get(auth_handlers::handle_me))
        .route("/api/users/search", get(users_handlers::handle_user_search))
        .route(
            "/api/notes",
            get(notes_handlers::handle_list_notes).post(notes_handlers::handle_create_note),
        )
        .route(
            "/api/notes/{noteId}",
            get(notes_handlers::handle_get_note)
                .put(notes_handlers::handle_update_note)
                .delete(notes_handlers::handle_delete_note),
        )
        .route(
            "/api/notes/{noteId}/shares",
            get(sharing_handlers::handle_list_shares).post(sharing_handlers::handle_create_share),
        )
        .route(
            "/api/notes/{noteId}/shares/{userId}",
            delete(sharing_handlers::handle_delete_share),
        )
        .route(
            "/api/notes/{noteId}/public-share",
            post(sharing_handlers::handle_create_public_share)
                .delete(sharing_handlers::handle_delete_public_share),
        )
        .route(
            "/api/public/notes/{token}",
            get(public_handlers::handle_public_note),
        )
        .with_state(deps.clone());

    // Mirrors backend-go's middleware stack, applied in the same order
    // (outermost first): request logging, then CORS, then session lookup,
    // then CSRF enforcement, then routing.
    router
        .layer(middleware::from_fn_with_state(
            deps.clone(),
            own_middleware::csrf_middleware,
        ))
        .layer(middleware::from_fn_with_state(
            deps.clone(),
            own_middleware::session_context_middleware,
        ))
        .layer(middleware::from_fn_with_state(
            deps.clone(),
            own_middleware::cors_middleware,
        ))
        .layer(middleware::from_fn(own_middleware::request_logger))
}
