use std::sync::Arc;

use axum::extract::{Path, State};
use axum::Json;

use super::dto::{to_public_note_view_dto, PublicNoteViewDto};
use super::respond::ApiError;
use super::server::Deps;

pub async fn handle_public_note(
    State(deps): State<Arc<Deps>>,
    Path(token): Path<String>,
) -> Result<Json<PublicNoteViewDto>, ApiError> {
    let view = deps.notes.get_public_note(&token).await?;
    Ok(Json(to_public_note_view_dto(view)))
}
