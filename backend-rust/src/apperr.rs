//! The small set of sentinel domain errors the HTTP layer maps to status
//! codes, so domain modules stay free of HTTP concerns.

use std::fmt;

// Note: there is no `VersionConflict` sentinel here (unlike backend-go's
// apperr.ErrVersionConflict) — an optimistic-concurrency conflict always
// needs the note's current server copy alongside it for the 409 body, so
// `notes::repository::UpdateOutcome`/`notes::service::UpdateResult` carry
// that directly instead of routing it through a plain error value.
#[derive(Debug)]
pub enum AppError {
    NotFound,
    Forbidden,
    Validation(String),
    Internal(String),
}

impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AppError::NotFound => write!(f, "not found"),
            AppError::Forbidden => write!(f, "forbidden"),
            AppError::Validation(msg) => write!(f, "validation failed: {msg}"),
            AppError::Internal(msg) => write!(f, "internal error: {msg}"),
        }
    }
}

impl std::error::Error for AppError {}

impl From<rusqlite::Error> for AppError {
    fn from(err: rusqlite::Error) -> Self {
        if matches!(err, rusqlite::Error::QueryReturnedNoRows) {
            AppError::NotFound
        } else {
            AppError::Internal(err.to_string())
        }
    }
}

pub type Result<T> = std::result::Result<T, AppError>;
