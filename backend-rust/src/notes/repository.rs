use std::collections::HashSet;

use chrono::Utc;
use rusqlite::{params, OptionalExtension};

use crate::apperr::{AppError, Result};
use crate::db::Db;
use crate::randtoken;
use crate::timefmt::{fmt_time, parse_time};

use super::cursor::{decode_cursor, encode_cursor};
use super::model::{Note, Page, Permission, PublicNoteView, PublicShare, Summary, UserShare};

/// 128 bits of entropy, see /docs/adr/0009-public-share-random-token.md.
const PUBLIC_SHARE_TOKEN_BYTES: usize = 16;

/// Outcome of an optimistic-concurrency-checked update: either it applied,
/// or the version didn't match and `Conflict` carries the note's current
/// server copy (for the caller to hand back in a 409 body).
pub enum UpdateOutcome {
    Updated(Note),
    Conflict(Note),
}

#[derive(Clone)]
pub struct Repository {
    db: Db,
}

fn row_to_note(row: &rusqlite::Row) -> rusqlite::Result<Note> {
    let public_note_id: Option<String> = row.get(7)?;
    Ok(Note {
        id: row.get(0)?,
        owner_id: row.get(1)?,
        title: row.get(2)?,
        content_markdown: row.get(3)?,
        version: row.get(4)?,
        created_at: parse_time(&row.get::<_, String>(5)?),
        updated_at: parse_time(&row.get::<_, String>(6)?),
        is_public: public_note_id.is_some(),
        my_permission: Permission::Owner, // caller (service layer) fills this in
    })
}

fn get_by_id_sync(conn: &rusqlite::Connection, id: &str) -> Result<Note> {
    conn.query_row(
        "SELECT n.id, n.owner_id, n.title, n.content_markdown, n.version, n.created_at, n.updated_at, nps.note_id
         FROM notes n
         LEFT JOIN note_public_shares nps ON nps.note_id = n.id
         WHERE n.id = ?1",
        [id],
        row_to_note,
    )
    .optional()?
    .ok_or(AppError::NotFound)
}

impl Repository {
    pub fn new(db: Db) -> Repository {
        Repository { db }
    }

    pub async fn create(&self, owner_id: String, title: String, content: String) -> Result<Note> {
        self.db
            .call(move |conn| {
                let id = uuid::Uuid::new_v4().to_string();
                let now = Utc::now();
                conn.execute(
                    "INSERT INTO notes (id, owner_id, title, content_markdown, version, created_at, updated_at)
                     VALUES (?1, ?2, ?3, ?4, 1, ?5, ?5)",
                    params![id, owner_id, title, content, fmt_time(now)],
                )?;
                Ok(Note {
                    id,
                    owner_id,
                    title,
                    content_markdown: content,
                    version: 1,
                    is_public: false,
                    created_at: now,
                    updated_at: now,
                    my_permission: Permission::Owner,
                })
            })
            .await
    }

    /// Fetches the raw note without regard to who is asking; callers (the
    /// service layer) are responsible for authorization decisions.
    pub async fn get_by_id(&self, id: String) -> Result<Note> {
        self.db.call(move |conn| get_by_id_sync(conn, &id)).await
    }

    /// Applies an optimistic-concurrency-checked edit (ADR 0008): it only
    /// succeeds if the row's current version still matches
    /// `expected_version`.
    pub async fn update(
        &self,
        id: String,
        title: String,
        content: String,
        expected_version: i64,
    ) -> Result<UpdateOutcome> {
        self.db
            .call(move |conn| {
                let now = fmt_time(Utc::now());
                let affected = conn.execute(
                    "UPDATE notes SET title = ?1, content_markdown = ?2, version = version + 1, updated_at = ?3
                     WHERE id = ?4 AND version = ?5",
                    params![title, content, now, id, expected_version],
                )?;
                if affected == 0 {
                    // not-found takes precedence over conflict
                    let current = get_by_id_sync(conn, &id)?;
                    return Ok(UpdateOutcome::Conflict(current));
                }
                Ok(UpdateOutcome::Updated(get_by_id_sync(conn, &id)?))
            })
            .await
    }

    pub async fn delete(&self, id: String) -> Result<()> {
        self.db
            .call(move |conn| {
                let affected = conn.execute("DELETE FROM notes WHERE id = ?1", [&id])?;
                if affected == 0 {
                    return Err(AppError::NotFound);
                }
                Ok(())
            })
            .await
    }

    /// Returns the explicit share permission granted to `user_id` on
    /// `note_id`, if any. Does not consider ownership.
    pub async fn share_permission(
        &self,
        note_id: String,
        user_id: String,
    ) -> Result<Option<Permission>> {
        self.db
            .call(move |conn| {
                let p: Option<String> = conn
                    .query_row(
                        "SELECT permission FROM note_shares WHERE note_id = ?1 AND user_id = ?2",
                        [&note_id, &user_id],
                        |row| row.get(0),
                    )
                    .optional()?;
                Ok(p.and_then(|p| Permission::parse(&p)))
            })
            .await
    }

    /// Returns a cursor page of notes owned by, or shared with, `user_id`,
    /// newest-edited first (ADR 0007).
    pub async fn list_for_viewer(
        &self,
        user_id: String,
        cursor: String,
        limit: i64,
    ) -> Result<Page> {
        self.db
            .call(move |conn| {
                let (cursor_updated_at, cursor_id) = if cursor.is_empty() {
                    (String::new(), String::new())
                } else {
                    let c = decode_cursor(&cursor).map_err(AppError::Validation)?;
                    (c.updated_at, c.id)
                };

                // The cursor placeholders (?4, ?5) are always bound, even
                // with no cursor, so the statement's parameter count is
                // constant; `?4 = ''` (never a valid timestamp) then makes
                // the comparison a no-op for the first page.
                let query = "SELECT n.id, n.title, n.content_markdown, n.owner_id, n.updated_at,
                            CASE WHEN n.owner_id = ?1 THEN 'owner' ELSE ns.permission END AS permission,
                            CASE WHEN nps.note_id IS NOT NULL THEN 1 ELSE 0 END AS is_public
                     FROM notes n
                     LEFT JOIN note_shares ns ON ns.note_id = n.id AND ns.user_id = ?2
                     LEFT JOIN note_public_shares nps ON nps.note_id = n.id
                     WHERE (n.owner_id = ?1 OR ns.user_id = ?2)
                     AND (?4 = '' OR (n.updated_at, n.id) < (?4, ?5))
                     ORDER BY n.updated_at DESC, n.id DESC
                     LIMIT ?3";

                let mut stmt = conn.prepare(query)?;
                let limit_plus_one = limit + 1;
                let rows = stmt.query_map(
                    params![user_id, user_id, limit_plus_one, cursor_updated_at, cursor_id],
                    |row| {
                        let is_public: i64 = row.get(6)?;
                        Ok(Summary {
                            id: row.get(0)?,
                            title: row.get(1)?,
                            content_markdown: row.get(2)?,
                            owner_id: row.get(3)?,
                            updated_at: parse_time(&row.get::<_, String>(4)?),
                            my_permission: Permission::parse(&row.get::<_, String>(5)?)
                                .unwrap_or(Permission::Read),
                            is_public: is_public == 1,
                        })
                    },
                )?;

                let mut items = Vec::new();
                for r in rows {
                    items.push(r?);
                }

                let mut page = Page::default();
                if items.len() as i64 > limit {
                    let last = items[(limit - 1) as usize].clone();
                    items.truncate(limit as usize);
                    page.next_cursor = encode_cursor(&fmt_time(last.updated_at), &last.id);
                }
                page.items = items;
                Ok(page)
            })
            .await
    }

    pub async fn list_shares(&self, note_id: String) -> Result<Vec<UserShare>> {
        self.db
            .call(move |conn| {
                let mut stmt = conn.prepare(
                    "SELECT u.id, u.display_name, COALESCE(u.avatar_url, ''), ns.permission, ns.created_at
                     FROM note_shares ns
                     JOIN users u ON u.id = ns.user_id
                     WHERE ns.note_id = ?1
                     ORDER BY ns.created_at",
                )?;
                let rows = stmt.query_map([&note_id], |row| {
                    Ok(UserShare {
                        user_id: row.get(0)?,
                        display_name: row.get(1)?,
                        avatar_url: row.get(2)?,
                        permission: Permission::parse(&row.get::<_, String>(3)?)
                            .unwrap_or(Permission::Read),
                        created_at: parse_time(&row.get::<_, String>(4)?),
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

    /// Grants (or changes the permission of) `user_id`'s access to
    /// `note_id`. The owner explicitly re-sharing an already-shared note
    /// still triggers a notification email (see service::share_with_user) —
    /// that's a deliberate simplification over tracking new-vs-changed
    /// shares.
    pub async fn upsert_share(
        &self,
        note_id: String,
        user_id: String,
        permission: Permission,
    ) -> Result<()> {
        self.db
            .call(move |conn| {
                conn.execute(
                    "INSERT INTO note_shares (note_id, user_id, permission, created_at)
                     VALUES (?1, ?2, ?3, ?4)
                     ON CONFLICT(note_id, user_id) DO UPDATE SET permission = excluded.permission",
                    params![note_id, user_id, permission.as_str(), fmt_time(Utc::now())],
                )?;
                Ok(())
            })
            .await
    }

    pub async fn delete_share(&self, note_id: String, user_id: String) -> Result<()> {
        self.db
            .call(move |conn| {
                let affected = conn.execute(
                    "DELETE FROM note_shares WHERE note_id = ?1 AND user_id = ?2",
                    [&note_id, &user_id],
                )?;
                if affected == 0 {
                    return Err(AppError::NotFound);
                }
                Ok(())
            })
            .await
    }

    pub async fn get_public_share(&self, note_id: String) -> Result<Option<PublicShare>> {
        self.db
            .call(move |conn| {
                let row: Option<(String, String)> = conn
                    .query_row(
                        "SELECT token, created_at FROM note_public_shares WHERE note_id = ?1",
                        [&note_id],
                        |row| Ok((row.get(0)?, row.get(1)?)),
                    )
                    .optional()?;
                Ok(row.map(|(token, created_at)| PublicShare {
                    token,
                    created_at: parse_time(&created_at),
                }))
            })
            .await
    }

    /// (Re)publishes `note_id` with a freshly generated, unguessable token,
    /// replacing any previous token (ADR 0009).
    pub async fn create_public_share(&self, note_id: String) -> Result<PublicShare> {
        self.db
            .call(move |conn| {
                let token = randtoken::new(PUBLIC_SHARE_TOKEN_BYTES);
                let now = Utc::now();
                conn.execute(
                    "INSERT INTO note_public_shares (note_id, token, created_at) VALUES (?1, ?2, ?3)
                     ON CONFLICT(note_id) DO UPDATE SET token = excluded.token, created_at = excluded.created_at",
                    params![note_id, token, fmt_time(now)],
                )?;
                Ok(PublicShare {
                    token,
                    created_at: now,
                })
            })
            .await
    }

    pub async fn delete_public_share(&self, note_id: String) -> Result<()> {
        self.db
            .call(move |conn| {
                let affected = conn.execute(
                    "DELETE FROM note_public_shares WHERE note_id = ?1",
                    [&note_id],
                )?;
                if affected == 0 {
                    return Err(AppError::NotFound);
                }
                Ok(())
            })
            .await
    }

    pub async fn get_by_public_token(&self, token: String) -> Result<PublicNoteView> {
        self.db
            .call(move |conn| {
                conn.query_row(
                    "SELECT n.title, n.content_markdown, n.updated_at
                     FROM note_public_shares nps
                     JOIN notes n ON n.id = nps.note_id
                     WHERE nps.token = ?1",
                    [&token],
                    |row| {
                        Ok(PublicNoteView {
                            title: row.get(0)?,
                            content_markdown: row.get(1)?,
                            updated_at: parse_time(&row.get::<_, String>(2)?),
                        })
                    },
                )
                .optional()?
                .ok_or(AppError::NotFound)
            })
            .await
    }

    /// Returns the set of user IDs already recorded as mentioned in
    /// `note_id`, so the caller can notify only newly added mentions.
    pub async fn existing_mentions(&self, note_id: String) -> Result<HashSet<String>> {
        self.db
            .call(move |conn| {
                let mut stmt =
                    conn.prepare("SELECT user_id FROM note_mentions WHERE note_id = ?1")?;
                let rows = stmt.query_map([&note_id], |row| row.get::<_, String>(0))?;
                let mut set = HashSet::new();
                for r in rows {
                    set.insert(r?);
                }
                Ok(set)
            })
            .await
    }

    pub async fn add_mentions(&self, note_id: String, user_ids: Vec<String>) -> Result<()> {
        self.db
            .call(move |conn| {
                let now = fmt_time(Utc::now());
                for uid in user_ids {
                    conn.execute(
                        "INSERT INTO note_mentions (note_id, user_id, created_at) VALUES (?1, ?2, ?3)
                         ON CONFLICT DO NOTHING",
                        params![note_id, uid, now],
                    )?;
                }
                Ok(())
            })
            .await
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::users;

    fn new_test_repo() -> (tempfile::TempDir, Repository, users::Repository) {
        let dir = tempfile::tempdir().expect("tempdir");
        let db =
            crate::db::Db::open(dir.path().join("test.db").to_str().unwrap()).expect("open db");
        (dir, Repository::new(db.clone()), users::Repository::new(db))
    }

    async fn must_create_user(repo: &users::Repository, subject: &str) -> users::User {
        repo.upsert_from_oidc(
            subject.to_string(),
            format!("{subject}@example.com"),
            format!("User {subject}"),
            String::new(),
        )
        .await
        .expect("create user")
    }

    #[tokio::test]
    async fn note_lifecycle() {
        let (_dir, repo, users_repo) = new_test_repo();
        let owner = must_create_user(&users_repo, "owner").await;

        let n = repo
            .create(
                owner.id.clone(),
                "Title".to_string(),
                "Some **content**".to_string(),
            )
            .await
            .expect("create");
        assert_eq!(n.version, 1);

        let fetched = repo.get_by_id(n.id.clone()).await.expect("get by id");
        assert_eq!(fetched.title, "Title");
        assert_eq!(fetched.owner_id, owner.id);

        let updated = match repo
            .update(
                n.id.clone(),
                "New title".to_string(),
                "New content".to_string(),
                n.version,
            )
            .await
            .expect("update")
        {
            UpdateOutcome::Updated(n) => n,
            UpdateOutcome::Conflict(_) => panic!("expected a successful update"),
        };
        assert_eq!(updated.version, 2);
        assert_eq!(updated.title, "New title");

        match repo
            .update(n.id.clone(), "Stale write".to_string(), "x".to_string(), 1)
            .await
            .expect("update with stale version should not error")
        {
            UpdateOutcome::Conflict(_) => {}
            UpdateOutcome::Updated(_) => panic!("expected a version conflict"),
        }

        repo.delete(n.id.clone()).await.expect("delete");
        assert!(matches!(
            repo.get_by_id(n.id).await,
            Err(AppError::NotFound)
        ));
    }

    #[tokio::test]
    async fn sharing_visibility() {
        let (_dir, repo, users_repo) = new_test_repo();
        let owner = must_create_user(&users_repo, "owner").await;
        let other = must_create_user(&users_repo, "other").await;

        let n = repo
            .create(
                owner.id.clone(),
                "Private".to_string(),
                "content".to_string(),
            )
            .await
            .expect("create");

        assert!(repo
            .share_permission(n.id.clone(), other.id.clone())
            .await
            .expect("share_permission")
            .is_none());

        let page = repo
            .list_for_viewer(other.id.clone(), String::new(), 12)
            .await
            .expect("list_for_viewer");
        assert!(
            page.items.is_empty(),
            "note should be invisible to a non-shared user"
        );

        repo.upsert_share(n.id.clone(), other.id.clone(), Permission::Read)
            .await
            .expect("upsert_share");

        let perm = repo
            .share_permission(n.id.clone(), other.id.clone())
            .await
            .expect("share_permission");
        assert_eq!(perm, Some(Permission::Read));

        let page = repo
            .list_for_viewer(other.id.clone(), String::new(), 12)
            .await
            .expect("list_for_viewer after share");
        assert_eq!(page.items.len(), 1);
        assert_eq!(page.items[0].my_permission, Permission::Read);

        repo.delete_share(n.id.clone(), other.id.clone())
            .await
            .expect("delete_share");
        assert!(repo
            .share_permission(n.id, other.id)
            .await
            .expect("share_permission")
            .is_none());
    }

    #[tokio::test]
    async fn public_share_uses_unguessable_token() {
        let (_dir, repo, users_repo) = new_test_repo();
        let owner = must_create_user(&users_repo, "owner").await;

        let n = repo
            .create(
                owner.id.clone(),
                "Public note".to_string(),
                "hello world".to_string(),
            )
            .await
            .expect("create");

        let ps = repo
            .create_public_share(n.id.clone())
            .await
            .expect("create_public_share");
        assert!(
            ps.token.len() >= 20,
            "token looks too short to be unguessable: {}",
            ps.token
        );
        assert_ne!(
            ps.token, n.id,
            "public token must not be derived from the note's own ID"
        );

        let view = repo
            .get_by_public_token(ps.token.clone())
            .await
            .expect("get_by_public_token");
        assert_eq!(view.title, "Public note");

        repo.delete_public_share(n.id)
            .await
            .expect("delete_public_share");
        assert!(matches!(
            repo.get_by_public_token(ps.token).await,
            Err(AppError::NotFound)
        ));
    }

    #[tokio::test]
    async fn mentions_are_notified_only_once() {
        let (_dir, repo, users_repo) = new_test_repo();
        let owner = must_create_user(&users_repo, "owner").await;
        let mentioned = must_create_user(&users_repo, "mentioned").await;

        let n = repo
            .create(owner.id, "Note".to_string(), "hi @mentioned".to_string())
            .await
            .expect("create");

        let existing = repo
            .existing_mentions(n.id.clone())
            .await
            .expect("existing_mentions");
        assert!(existing.is_empty());

        repo.add_mentions(n.id.clone(), vec![mentioned.id.clone()])
            .await
            .expect("add_mentions");

        let existing = repo
            .existing_mentions(n.id.clone())
            .await
            .expect("existing_mentions");
        assert!(existing.contains(&mentioned.id));

        // Adding the same mention again must stay idempotent (no duplicate
        // row/error).
        repo.add_mentions(n.id, vec![mentioned.id])
            .await
            .expect("add_mentions repeat");
    }

    #[tokio::test]
    async fn list_for_viewer_returns_full_markdown() {
        let (_dir, repo, users_repo) = new_test_repo();
        let owner = must_create_user(&users_repo, "owner").await;

        let body =
            "# Heading\n\nSome **bold** text and a [link](https://example.com)\n\n- one\n- two";
        repo.create(owner.id.clone(), "Note".to_string(), body.to_string())
            .await
            .expect("create");

        let page = repo
            .list_for_viewer(owner.id, String::new(), 10)
            .await
            .expect("list_for_viewer");
        assert_eq!(page.items.len(), 1);
        assert_eq!(
            page.items[0].content_markdown, body,
            "dashboard feed must not alter markdown"
        );
    }

    #[tokio::test]
    async fn list_for_viewer_cursor_pagination() {
        let (_dir, repo, users_repo) = new_test_repo();
        let owner = must_create_user(&users_repo, "owner").await;

        const TOTAL: usize = 5;
        let mut ids = std::collections::HashSet::new();
        for _ in 0..TOTAL {
            let n = repo
                .create(owner.id.clone(), "Note".to_string(), "content".to_string())
                .await
                .expect("create");
            ids.insert(n.id);
            // Ensure distinct updated_at ordering.
            tokio::time::sleep(std::time::Duration::from_millis(2)).await;
        }

        let mut seen = std::collections::HashSet::new();
        let mut cursor = String::new();
        for pages in 0..=TOTAL {
            assert!(pages < TOTAL, "pagination did not terminate");
            let page = repo
                .list_for_viewer(owner.id.clone(), cursor.clone(), 2)
                .await
                .expect("list_for_viewer");
            for item in &page.items {
                assert!(
                    seen.insert(item.id.clone()),
                    "note {} returned twice across pages",
                    item.id
                );
            }
            if page.next_cursor.is_empty() {
                break;
            }
            cursor = page.next_cursor;
        }

        assert_eq!(seen.len(), TOTAL);
        for id in &ids {
            assert!(
                seen.contains(id),
                "note {id} was never returned by pagination"
            );
        }
    }
}
