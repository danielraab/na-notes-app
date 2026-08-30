use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;

use crate::notes::UpdateResult;

use super::dto::{to_note_dto, to_note_page_dto, NoteInputDto};
use super::middleware::{OptionalAuth, RequireAuth};
use super::respond::ApiError;
use super::server::Deps;

const MAX_PAGE_LIMIT: i64 = 50;

#[derive(Deserialize)]
pub struct ListNotesQuery {
    #[serde(default)]
    cursor: String,
    limit: Option<String>,
}

pub async fn handle_list_notes(
    State(deps): State<Arc<Deps>>,
    OptionalAuth(user_id): OptionalAuth,
    Query(q): Query<ListNotesQuery>,
) -> Result<Response, ApiError> {
    let mut limit: i64 = 12;
    if let Some(raw) = q.limit.filter(|s| !s.is_empty()) {
        match raw.parse::<i64>() {
            Ok(v) if v > 0 => limit = v,
            _ => {
                return Err(ApiError::new(
                    StatusCode::BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "limit must be a positive integer",
                ))
            }
        }
    }
    if limit > MAX_PAGE_LIMIT {
        limit = MAX_PAGE_LIMIT;
    }

    let page = deps.notes.list(user_id.as_deref(), q.cursor, limit).await?;
    Ok(Json(to_note_page_dto(page)).into_response())
}

pub async fn handle_create_note(
    State(deps): State<Arc<Deps>>,
    RequireAuth(user_id): RequireAuth,
    body: Result<Json<NoteInputDto>, axum::extract::rejection::JsonRejection>,
) -> Result<Response, ApiError> {
    let Json(input) = body.map_err(|_| bad_body())?;
    let n = deps
        .notes
        .create(
            &user_id,
            input.title,
            input.content_markdown,
            input.mentioned_user_ids,
        )
        .await?;
    Ok((StatusCode::CREATED, Json(to_note_dto(n))).into_response())
}

pub async fn handle_get_note(
    State(deps): State<Arc<Deps>>,
    RequireAuth(user_id): RequireAuth,
    Path(note_id): Path<String>,
) -> Result<Response, ApiError> {
    let n = deps.notes.get(&note_id, &user_id).await?;
    Ok(Json(to_note_dto(n)).into_response())
}

pub async fn handle_update_note(
    State(deps): State<Arc<Deps>>,
    RequireAuth(user_id): RequireAuth,
    Path(note_id): Path<String>,
    headers: HeaderMap,
    body: Result<Json<NoteInputDto>, axum::extract::rejection::JsonRejection>,
) -> Result<Response, ApiError> {
    let expected_version: i64 = headers
        .get("If-Match")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.parse().ok())
        .ok_or_else(|| {
            ApiError::new(
                StatusCode::BAD_REQUEST,
                "VALIDATION_ERROR",
                "If-Match header must be the note's current version",
            )
        })?;

    let Json(input) = body.map_err(|_| bad_body())?;

    let result = deps
        .notes
        .update(
            &note_id,
            &user_id,
            expected_version,
            input.title,
            input.content_markdown,
            input.mentioned_user_ids,
        )
        .await?;

    match result {
        UpdateResult::Updated(n) => Ok(Json(to_note_dto(n)).into_response()),
        UpdateResult::Conflict(n) => {
            Ok((StatusCode::CONFLICT, Json(to_note_dto(n))).into_response())
        }
    }
}

pub async fn handle_delete_note(
    State(deps): State<Arc<Deps>>,
    RequireAuth(user_id): RequireAuth,
    Path(note_id): Path<String>,
) -> Result<StatusCode, ApiError> {
    deps.notes.delete(&note_id, &user_id).await?;
    Ok(StatusCode::NO_CONTENT)
}

fn bad_body() -> ApiError {
    ApiError::new(
        StatusCode::BAD_REQUEST,
        "VALIDATION_ERROR",
        "invalid request body",
    )
}
