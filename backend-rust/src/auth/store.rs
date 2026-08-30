//! The OIDC authorization-code+PKCE login flow's server-side session and
//! in-flight login state (ADR 0004).

use chrono::{DateTime, Duration, Utc};
use rusqlite::{params, OptionalExtension};

use crate::apperr::{AppError, Result};
use crate::db::Db;
use crate::randtoken;
use crate::timefmt::{fmt_time, parse_time};

const SESSION_ID_BYTES: usize = 32;
const CSRF_TOKEN_BYTES: usize = 32;
const SESSION_TTL_DAYS: i64 = 7;
const OIDC_REQUEST_TTL_MINUTES: i64 = 10;
const OIDC_STATE_BYTES: usize = 24;
const CODE_VERIFIER_BYTES: usize = 32;

#[derive(Debug, Clone)]
pub struct Session {
    pub id: String,
    pub user_id: String,
    pub csrf_token: String,
    pub expires_at: DateTime<Utc>,
}

/// The server-side record of an in-flight login, keyed by the OAuth
/// `state`. The PKCE `code_verifier` must never be exposed to the browser,
/// so it's kept here rather than in a client-readable cookie.
#[derive(Debug, Clone)]
pub struct OidcRequest {
    pub state: String,
    pub code_verifier: String,
    pub redirect_to: String,
}

#[derive(Clone)]
pub struct Store {
    db: Db,
}

impl Store {
    pub fn new(db: Db) -> Store {
        Store { db }
    }

    pub async fn create_session(&self, user_id: String) -> Result<Session> {
        self.db
            .call(move |conn| {
                let id = randtoken::new(SESSION_ID_BYTES);
                let csrf = randtoken::new(CSRF_TOKEN_BYTES);
                let now = Utc::now();
                let expires_at = now + Duration::days(SESSION_TTL_DAYS);
                conn.execute(
                    "INSERT INTO sessions (id, user_id, csrf_token, expires_at, created_at) VALUES (?1, ?2, ?3, ?4, ?5)",
                    params![id, user_id, csrf, fmt_time(expires_at), fmt_time(now)],
                )?;
                Ok(Session {
                    id,
                    user_id,
                    csrf_token: csrf,
                    expires_at,
                })
            })
            .await
    }

    pub async fn get_session(&self, id: String) -> Result<Session> {
        self.db
            .call(move |conn| {
                let row: Option<(String, String, String, String)> = conn
                    .query_row(
                        "SELECT id, user_id, csrf_token, expires_at FROM sessions WHERE id = ?1",
                        [&id],
                        |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
                    )
                    .optional()?;
                let Some((id, user_id, csrf_token, expires_at)) = row else {
                    return Err(AppError::NotFound);
                };
                let expires_at = parse_time(&expires_at);
                if Utc::now() > expires_at {
                    conn.execute("DELETE FROM sessions WHERE id = ?1", [&id])?;
                    return Err(AppError::NotFound);
                }
                Ok(Session {
                    id,
                    user_id,
                    csrf_token,
                    expires_at,
                })
            })
            .await
    }

    pub async fn delete_session(&self, id: String) -> Result<()> {
        self.db
            .call(move |conn| {
                conn.execute("DELETE FROM sessions WHERE id = ?1", [&id])?;
                Ok(())
            })
            .await
    }

    /// Starts a login attempt. Also opportunistically clears expired
    /// requests, since they're otherwise never cleaned up (abandoned
    /// logins are the only source of them, and volume is low).
    pub async fn create_oidc_request(&self, redirect_to: String) -> Result<OidcRequest> {
        self.db
            .call(move |conn| {
                conn.execute(
                    "DELETE FROM oidc_requests WHERE expires_at < ?1",
                    [fmt_time(Utc::now())],
                )?;

                let state = randtoken::new(OIDC_STATE_BYTES);
                let verifier = randtoken::new(CODE_VERIFIER_BYTES);
                let expires_at = Utc::now() + Duration::minutes(OIDC_REQUEST_TTL_MINUTES);
                conn.execute(
                    "INSERT INTO oidc_requests (state, code_verifier, redirect_to, expires_at) VALUES (?1, ?2, ?3, ?4)",
                    params![state, verifier, redirect_to, fmt_time(expires_at)],
                )?;
                Ok(OidcRequest {
                    state,
                    code_verifier: verifier,
                    redirect_to,
                })
            })
            .await
    }

    /// Looks up and deletes the request in one step: a state value must
    /// only ever be usable once.
    pub async fn consume_oidc_request(&self, state: String) -> Result<OidcRequest> {
        self.db
            .call(move |conn| {
                let row: Option<(String, String, String, String)> = conn
                    .query_row(
                        "SELECT state, code_verifier, redirect_to, expires_at FROM oidc_requests WHERE state = ?1",
                        [&state],
                        |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
                    )
                    .optional()?;
                let Some((state, code_verifier, redirect_to, expires_at)) = row else {
                    return Err(AppError::NotFound);
                };
                conn.execute("DELETE FROM oidc_requests WHERE state = ?1", [&state])?;
                if Utc::now() > parse_time(&expires_at) {
                    return Err(AppError::NotFound);
                }
                Ok(OidcRequest {
                    state,
                    code_verifier,
                    redirect_to,
                })
            })
            .await
    }
}
