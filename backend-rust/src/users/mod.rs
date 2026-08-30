//! User accounts. Accounts are created lazily on first successful OIDC
//! login; there is no separate registration flow.

use chrono::{DateTime, Utc};
use rusqlite::{params, OptionalExtension};

use crate::apperr::{AppError, Result};
use crate::db::Db;
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
        self.db
            .call(move |conn| {
                let avatar_url = if avatar_url.is_empty() {
                    None
                } else {
                    Some(avatar_url)
                };

                let existing = conn
                    .query_row(
                        "SELECT id, created_at FROM users WHERE oidc_subject = ?1",
                        [&subject],
                        |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?)),
                    )
                    .optional()?;

                match existing {
                    None => {
                        let id = uuid::Uuid::new_v4().to_string();
                        let now = Utc::now();
                        conn.execute(
                            "INSERT INTO users (id, oidc_subject, email, display_name, avatar_url, created_at)
                             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
                            params![id, subject, email, display_name, avatar_url, fmt_time(now)],
                        )?;
                        Ok(User {
                            id,
                            email,
                            display_name,
                            avatar_url,
                            created_at: now,
                        })
                    }
                    Some((id, created_at)) => {
                        conn.execute(
                            "UPDATE users SET email = ?1, display_name = ?2, avatar_url = ?3 WHERE id = ?4",
                            params![email, display_name, avatar_url, id],
                        )?;
                        Ok(User {
                            id,
                            email,
                            display_name,
                            avatar_url,
                            created_at: parse_time(&created_at),
                        })
                    }
                }
            })
            .await
    }

    pub async fn get_by_id(&self, id: String) -> Result<User> {
        self.db
            .call(move |conn| {
                conn.query_row(
                    "SELECT id, email, display_name, avatar_url, created_at FROM users WHERE id = ?1",
                    [&id],
                    |row| {
                        Ok(User {
                            id: row.get(0)?,
                            email: row.get(1)?,
                            display_name: row.get(2)?,
                            avatar_url: row.get(3)?,
                            created_at: parse_time(&row.get::<_, String>(4)?),
                        })
                    },
                )
                .map_err(AppError::from)
            })
            .await
    }

    /// Returns users whose display name or email starts with `q`, excluding
    /// the caller, for mention/share autocomplete.
    pub async fn search(
        &self,
        exclude_user_id: String,
        q: String,
        limit: i64,
    ) -> Result<Vec<Summary>> {
        self.db
            .call(move |conn| {
                let like = format!("{}%", q.to_lowercase());
                let mut stmt = conn.prepare(
                    "SELECT id, display_name, avatar_url FROM users
                     WHERE id != ?1 AND (LOWER(display_name) LIKE ?2 OR LOWER(email) LIKE ?2)
                     ORDER BY display_name LIMIT ?3",
                )?;
                let rows = stmt.query_map(params![exclude_user_id, like, limit], |row| {
                    Ok(Summary {
                        id: row.get(0)?,
                        display_name: row.get(1)?,
                        avatar_url: row.get(2)?,
                    })
                })?;
                let mut out = Vec::new();
                for r in rows {
                    out.push(r?);
                }
                Ok(out)
            })
            .await
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn new_test_repo() -> (tempfile::TempDir, Repository) {
        let dir = tempfile::tempdir().expect("tempdir");
        let db =
            crate::db::Db::open(dir.path().join("test.db").to_str().unwrap()).expect("open db");
        (dir, Repository::new(db))
    }

    // Regression test: created_at is stored as TEXT and must be parsed
    // back into DateTime<Utc> by hand — rusqlite returns TEXT columns as
    // strings, so a mismatched column type compiles but fails at run time
    // on every read.
    #[tokio::test]
    async fn upsert_and_get_by_id_round_trips_created_at() {
        let (_dir, repo) = new_test_repo();

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
        let (_dir, repo) = new_test_repo();

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
