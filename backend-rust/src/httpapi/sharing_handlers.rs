use std::sync::Arc;

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;

use crate::notes::Permission;

use super::dto::{to_public_share_dto, to_user_share_dto, CreateShareDto, NoteSharesDto};
use super::middleware::RequireAuth;
use super::respond::ApiError;
use super::server::Deps;

pub async fn handle_list_shares(
    State(deps): State<Arc<Deps>>,
    RequireAuth(user_id): RequireAuth,
    Path(note_id): Path<String>,
) -> Result<Response, ApiError> {
    let (shares, public) = deps.notes.list_shares(&note_id, &user_id).await?;
    let user_shares = shares.into_iter().map(to_user_share_dto).collect();
    let public_share = public.map(|ps| {
        let url = format!("{}/shared/{}", deps.config.frontend_url, ps.token);
        to_public_share_dto(ps, url)
    });
    Ok(Json(NoteSharesDto {
        user_shares,
        public_share,
    })
    .into_response())
}

pub async fn handle_create_share(
    State(deps): State<Arc<Deps>>,
    RequireAuth(user_id): RequireAuth,
    Path(note_id): Path<String>,
    body: Result<Json<CreateShareDto>, axum::extract::rejection::JsonRejection>,
) -> Result<Response, ApiError> {
    let Json(input) = body.map_err(|_| {
        ApiError::new(
            StatusCode::BAD_REQUEST,
            "VALIDATION_ERROR",
            "invalid request body",
        )
    })?;

    let permission = match input.permission.as_str() {
        "read" => Permission::Read,
        "edit" => Permission::Edit,
        _ => {
            return Err(ApiError::new(
                StatusCode::BAD_REQUEST,
                "VALIDATION_ERROR",
                "permission must be 'read' or 'edit'",
            ))
        }
    };

    let share = deps
        .notes
        .share_with_user(&note_id, &user_id, &input.user_id, permission)
        .await?;
    Ok((StatusCode::CREATED, Json(to_user_share_dto(share))).into_response())
}

pub async fn handle_delete_share(
    State(deps): State<Arc<Deps>>,
    RequireAuth(user_id): RequireAuth,
    Path((note_id, target_user_id)): Path<(String, String)>,
) -> Result<StatusCode, ApiError> {
    deps.notes
        .revoke_share(&note_id, &user_id, &target_user_id)
        .await?;
    Ok(StatusCode::NO_CONTENT)
}

pub async fn handle_create_public_share(
    State(deps): State<Arc<Deps>>,
    RequireAuth(user_id): RequireAuth,
    Path(note_id): Path<String>,
) -> Result<Response, ApiError> {
    let (ps, url) = deps.notes.create_public_share(&note_id, &user_id).await?;
    Ok((StatusCode::CREATED, Json(to_public_share_dto(ps, url))).into_response())
}

pub async fn handle_delete_public_share(
    State(deps): State<Arc<Deps>>,
    RequireAuth(user_id): RequireAuth,
    Path(note_id): Path<String>,
) -> Result<StatusCode, ApiError> {
    deps.notes.revoke_public_share(&note_id, &user_id).await?;
    Ok(StatusCode::NO_CONTENT)
}
