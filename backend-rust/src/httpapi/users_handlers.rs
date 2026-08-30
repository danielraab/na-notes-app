use std::sync::Arc;

use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::Json;
use serde::Deserialize;

use super::dto::{to_user_summary_dto, UserSummaryDto};
use super::middleware::RequireAuth;
use super::respond::ApiError;
use super::server::Deps;

const MAX_USER_SEARCH_LIMIT: i64 = 25;

#[derive(Deserialize)]
pub struct SearchQuery {
    #[serde(default)]
    q: String,
    limit: Option<String>,
}

pub async fn handle_user_search(
    State(deps): State<Arc<Deps>>,
    RequireAuth(user_id): RequireAuth,
    Query(q): Query<SearchQuery>,
) -> Result<Json<Vec<UserSummaryDto>>, ApiError> {
    if q.q.is_empty() {
        return Err(ApiError::new(
            StatusCode::BAD_REQUEST,
            "VALIDATION_ERROR",
            "q is required",
        ));
    }

    let mut limit: i64 = 10;
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
    if limit > MAX_USER_SEARCH_LIMIT {
        limit = MAX_USER_SEARCH_LIMIT;
    }

    let results = deps.users.search(user_id, q.q, limit).await?;
    Ok(Json(results.into_iter().map(to_user_summary_dto).collect()))
}
