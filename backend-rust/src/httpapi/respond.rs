use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde_json::json;

use crate::apperr::AppError;

/// The single error response shape (`{"error": {"code", "message"}}`)
/// every endpoint uses, and the small mapping from `apperr::AppError` onto
/// HTTP status codes. Anything unexpected is logged with detail the client
/// never sees.
pub struct ApiError {
    pub status: StatusCode,
    pub code: &'static str,
    pub message: String,
}

impl ApiError {
    pub fn new(status: StatusCode, code: &'static str, message: impl Into<String>) -> ApiError {
        ApiError {
            status,
            code,
            message: message.into(),
        }
    }

    pub fn unauthenticated() -> ApiError {
        ApiError::new(
            StatusCode::UNAUTHORIZED,
            "UNAUTHENTICATED",
            "login required",
        )
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let body = json!({ "error": { "code": self.code, "message": self.message } });
        (self.status, Json(body)).into_response()
    }
}

impl From<AppError> for ApiError {
    fn from(err: AppError) -> Self {
        match err {
            AppError::NotFound => {
                ApiError::new(StatusCode::NOT_FOUND, "NOT_FOUND", "resource not found")
            }
            AppError::Forbidden => {
                ApiError::new(StatusCode::FORBIDDEN, "FORBIDDEN", "not permitted")
            }
            AppError::Validation(msg) => {
                ApiError::new(StatusCode::BAD_REQUEST, "VALIDATION_ERROR", msg)
            }
            AppError::Internal(msg) => {
                tracing::error!(error = %msg, "unhandled error");
                ApiError::new(
                    StatusCode::INTERNAL_SERVER_ERROR,
                    "INTERNAL",
                    "internal server error",
                )
            }
        }
    }
}
