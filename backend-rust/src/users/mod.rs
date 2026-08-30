//! User accounts. Accounts are created lazily on first successful OIDC
//! login; there is no separate registration flow.

use chrono::{DateTime, Utc};

use crate::apperr::{AppError, Result};
use crate::db::{Db, Row};
use crate::params;
use crate::timefmt::{fmt_time, parse_time};

#[derive(Debug, Clone)]
pub struct User {
    pub id: String,
    pub email: String,
    pub display_name: String,
    pub avatar_url: Option<String>,
    // Part of the domain model (mirrors backend-go's users.User.CreatedAt)
    // even though no DTO currently exposes it — the OpenAPI `User` schema
    // doesn't include createdAt either. Read back and asserted in tests.
    #[allow(dead_code)]
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone)]
pub struct Summary {
    pub id: String,
    pub display_name: String,
    pub avatar_url: Option<String>,
}

#[derive(Clone)]
pub struct Repository {
    db: Db,
}

impl Repository {
    pub fn new(db: Db) -> Repository {
        Repository { db }
    }

    /// Creates the user on first login, or refreshes their profile fields
    /// (display name/avatar can change at the identity provider) on
    /// subsequent logins. Matching is on the stable OIDC subject, never on
    /// email alone, since some providers allow email reuse/change.
    pub async fn upsert_from_oidc(
        &self,
        subject: String,
        email: String,
        display_name: String,
        avatar_url: String,
    ) -> Result<User> {
        let avatar_url = if avatar_url.is_empty() {
            None
        } else {
            Some(avatar_url)
        };

        let existing = self
            .db
            .query_opt(
                "SELECT id, created_at FROM users WHERE oidc_subject = ?1",
                params![&subject],
            )
            .await?;

        match existing {
            None => {
                let id = uuid::Uuid::new_v4().to_string();
                let now = Utc::now();
                self.db
                    .execute(
                        "INSERT INTO users (id, oidc_subject, email, display_name, avatar_url, created_at)
                         VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
                        params![&id, subject, &email, &display_name, &avatar_url, fmt_time(now)],
                    )
                    .await?;
                Ok(User {
                    id,
                    email,
                    display_name,
                    avatar_url,
                    created_at: now,
                })
            }
            Some(row) => {
                let id = row.text(0)?;
                let created_at = parse_time(&row.text(1)?);
                self.db
                    .execute(
                        "UPDATE users SET email = ?1, display_name = ?2, avatar_url = ?3 WHERE id = ?4",
                        params![&email, &display_name, &avatar_url, &id],
                    )
                    .await?;
                Ok(User {
                    id,
                    email,
                    display_name,
                    avatar_url,
                    created_at,
                })
            }
        }
    }

    pub async fn get_by_id(&self, id: String) -> Result<User> {
        let row = self
            .db
            .query_opt(
                "SELECT id, email, display_name, avatar_url, created_at FROM users WHERE id = ?1",
                params![id],
            )
            .await?
            .ok_or(AppError::NotFound)?;
        Ok(User {
            id: row.text(0)?,
            email: row.text(1)?,
            display_name: row.text(2)?,
            avatar_url: row.opt_text(3)?,
            created_at: parse_time(&row.text(4)?),
        })
    }

    /// Returns users whose display name or email starts with `q`, excluding
    /// the caller, for mention/share autocomplete.
    pub async fn search(
        &self,
        exclude_user_id: String,
        q: String,
        limit: i64,
    ) -> Result<Vec<Summary>> {
        let like = format!("{}%", q.to_lowercase());
        let rows = self
            .db
            .query_all(
                "SELECT id, display_name, avatar_url FROM users
                 WHERE id != ?1 AND (LOWER(display_name) LIKE ?2 OR LOWER(email) LIKE ?2)
                 ORDER BY display_name LIMIT ?3",
                params![exclude_user_id, like, limit],
            )
            .await?;
        rows.iter().map(row_to_summary).collect()
    }
}

fn row_to_summary(row: &Row) -> Result<Summary> {
    Ok(Summary {
        id: row.text(0)?,
        display_name: row.text(1)?,
        avatar_url: row.opt_text(2)?,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::testsupport::TestDb;

    // Regression test: created_at is stored as TEXT and must be parsed
    // back into DateTime<Utc> by hand — neither engine hands back a
    // timestamp type for it, so a mismatched read fails at run time.
    #[tokio::test]
    async fn upsert_and_get_by_id_round_trips_created_at() {
        let db = TestDb::open().await;
        let repo = Repository::new(db.handle());

        let created = repo
            .upsert_from_oidc(
                "subject-1".to_string(),
                "alice@example.com".to_string(),
                "Alice".to_string(),
                String::new(),
            )
            .await
            .expect("create");

        let fetched = repo.get_by_id(created.id.clone()).await.expect("get by id");
        assert_eq!(fetched.id, created.id);
        assert_eq!(fetched.email, "alice@example.com");
        assert_eq!(fetched.created_at, created.created_at);

        // Second login with the same subject updates the profile rather
        // than creating a second account, and must still round-trip
        // created_at.
        let updated = repo
            .upsert_from_oidc(
                "subject-1".to_string(),
                "alice2@example.com".to_string(),
                "Alice Updated".to_string(),
                String::new(),
            )
            .await
            .expect("update");
        assert_eq!(updated.id, created.id, "second login created a new user");
        assert_eq!(updated.email, "alice2@example.com");
        assert_eq!(updated.display_name, "Alice Updated");
        assert_eq!(
            updated.created_at, created.created_at,
            "created_at changed on update"
        );
    }

    #[tokio::test]
    async fn search_excludes_caller() {
        let db = TestDb::open().await;
        let repo = Repository::new(db.handle());

        let me = repo
            .upsert_from_oidc(
                "me".to_string(),
                "me@example.com".to_string(),
                "Me".to_string(),
                String::new(),
            )
            .await
            .expect("create me");
        repo.upsert_from_oidc(
            "alice".to_string(),
            "alice@example.com".to_string(),
            "Alice".to_string(),
            String::new(),
        )
        .await
        .expect("create alice");

        let results = repo
            .search(me.id.clone(), "Al".to_string(), 10)
            .await
            .expect("search");
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].display_name, "Alice");

        let results = repo
            .search(me.id, "Me".to_string(), 10)
            .await
            .expect("search");
        assert!(results.is_empty(), "search should exclude the caller");
    }
}
