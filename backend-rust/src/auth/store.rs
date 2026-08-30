//! The OIDC authorization-code+PKCE login flow's server-side session and
//! in-flight login state (ADR 0004).

use chrono::{DateTime, Duration, Utc};

use crate::apperr::{AppError, Result};
use crate::db::Db;
use crate::params;
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
        let id = randtoken::new(SESSION_ID_BYTES);
        let csrf = randtoken::new(CSRF_TOKEN_BYTES);
        let now = Utc::now();
        let expires_at = now + Duration::days(SESSION_TTL_DAYS);

        self.db
            .execute(
                "INSERT INTO sessions (id, user_id, csrf_token, expires_at, created_at)
                 VALUES (?1, ?2, ?3, ?4, ?5)",
                params![&id, &user_id, &csrf, fmt_time(expires_at), fmt_time(now)],
            )
            .await?;

        Ok(Session {
            id,
            user_id,
            csrf_token: csrf,
            expires_at,
        })
    }

    pub async fn get_session(&self, id: String) -> Result<Session> {
        let row = self
            .db
            .query_opt(
                "SELECT id, user_id, csrf_token, expires_at FROM sessions WHERE id = ?1",
                params![&id],
            )
            .await?
            .ok_or(AppError::NotFound)?;

        let session = Session {
            id: row.text(0)?,
            user_id: row.text(1)?,
            csrf_token: row.text(2)?,
            expires_at: parse_time(&row.text(3)?),
        };

        if Utc::now() > session.expires_at {
            self.delete_session(session.id).await?;
            return Err(AppError::NotFound);
        }
        Ok(session)
    }

    pub async fn delete_session(&self, id: String) -> Result<()> {
        self.db
            .execute("DELETE FROM sessions WHERE id = ?1", params![id])
            .await?;
        Ok(())
    }

    /// Starts a login attempt. Also opportunistically clears expired
    /// requests, since they're otherwise never cleaned up (abandoned
    /// logins are the only source of them, and volume is low).
    pub async fn create_oidc_request(&self, redirect_to: String) -> Result<OidcRequest> {
        self.db
            .execute(
                "DELETE FROM oidc_requests WHERE expires_at < ?1",
                params![fmt_time(Utc::now())],
            )
            .await?;

        let state = randtoken::new(OIDC_STATE_BYTES);
        let code_verifier = randtoken::new(CODE_VERIFIER_BYTES);
        let expires_at = Utc::now() + Duration::minutes(OIDC_REQUEST_TTL_MINUTES);

        self.db
            .execute(
                "INSERT INTO oidc_requests (state, code_verifier, redirect_to, expires_at)
                 VALUES (?1, ?2, ?3, ?4)",
                params![&state, &code_verifier, &redirect_to, fmt_time(expires_at)],
            )
            .await?;

        Ok(OidcRequest {
            state,
            code_verifier,
            redirect_to,
        })
    }

    /// Looks up and deletes the request in one step: a state value must
    /// only ever be usable once.
    pub async fn consume_oidc_request(&self, state: String) -> Result<OidcRequest> {
        let row = self
            .db
            .query_opt(
                "SELECT state, code_verifier, redirect_to, expires_at FROM oidc_requests WHERE state = ?1",
                params![&state],
            )
            .await?
            .ok_or(AppError::NotFound)?;

        let request = OidcRequest {
            state: row.text(0)?,
            code_verifier: row.text(1)?,
            redirect_to: row.text(2)?,
        };
        let expires_at = parse_time(&row.text(3)?);

        self.db
            .execute(
                "DELETE FROM oidc_requests WHERE state = ?1",
                params![&request.state],
            )
            .await?;

        if Utc::now() > expires_at {
            return Err(AppError::NotFound);
        }
        Ok(request)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::testsupport::TestDb;
    use crate::users;

    #[tokio::test]
    async fn session_round_trips_and_deletes() {
        let db = TestDb::open().await;
        let users_repo = users::Repository::new(db.handle());
        let store = Store::new(db.handle());

        let user = users_repo
            .upsert_from_oidc(
                "subject".to_string(),
                "u@example.com".to_string(),
                "U".to_string(),
                String::new(),
            )
            .await
            .expect("create user");

        let created = store
            .create_session(user.id.clone())
            .await
            .expect("create_session");
        assert!(!created.csrf_token.is_empty());

        let fetched = store
            .get_session(created.id.clone())
            .await
            .expect("get_session");
        assert_eq!(fetched.user_id, user.id);
        assert_eq!(fetched.csrf_token, created.csrf_token);

        store
            .delete_session(created.id.clone())
            .await
            .expect("delete_session");
        assert!(matches!(
            store.get_session(created.id).await,
            Err(AppError::NotFound)
        ));
    }

    #[tokio::test]
    async fn oidc_request_is_single_use() {
        let db = TestDb::open().await;
        let store = Store::new(db.handle());

        let created = store
            .create_oidc_request("/notes".to_string())
            .await
            .expect("create_oidc_request");
        assert!(!created.code_verifier.is_empty());

        let consumed = store
            .consume_oidc_request(created.state.clone())
            .await
            .expect("consume_oidc_request");
        assert_eq!(consumed.code_verifier, created.code_verifier);
        assert_eq!(consumed.redirect_to, "/notes");

        // A state value must never be usable twice.
        assert!(matches!(
            store.consume_oidc_request(created.state).await,
            Err(AppError::NotFound)
        ));
    }
}
