use chrono::Utc;

use crate::apperr::{AppError, Result};
use crate::mail::Mailer;
use crate::users;

use super::model::{Note, Page, Permission, PublicNoteView, PublicShare, Summary, UserShare};
use super::repository::{Repository, UpdateOutcome};

pub const INITIAL_PAGE_SIZE: i64 = 12;

fn sample_note() -> Summary {
    Summary {
        id: "00000000-0000-0000-0000-000000000000".to_string(),
        title: "Welcome to NA Notes".to_string(),
        content_markdown: "Sign in to create your own notes, share them with teammates, and mention people to loop them in.".to_string(),
        owner_id: "00000000-0000-0000-0000-000000000000".to_string(),
        my_permission: Permission::Read,
        is_public: true,
        updated_at: Utc::now(),
    }
}

pub enum UpdateResult {
    Updated(Note),
    Conflict(Note),
}

#[derive(Clone)]
pub struct Service {
    repo: Repository,
    users: users::Repository,
    mailer: Mailer,
    /// Frontend origin, for links in emails and public share URLs.
    base_url: String,
}

impl Service {
    pub fn new(
        repo: Repository,
        users: users::Repository,
        mailer: Mailer,
        frontend_base_url: String,
    ) -> Service {
        Service {
            repo,
            users,
            mailer,
            base_url: frontend_base_url,
        }
    }

    /// Returns the dashboard feed. An anonymous viewer (`viewer_id` is
    /// `None`) always sees exactly the sample note, per the product spec.
    pub async fn list(&self, viewer_id: Option<&str>, cursor: String, limit: i64) -> Result<Page> {
        let Some(viewer_id) = viewer_id else {
            return Ok(Page {
                items: vec![sample_note()],
                next_cursor: String::new(),
            });
        };
        let limit = if limit <= 0 { INITIAL_PAGE_SIZE } else { limit };
        self.repo
            .list_for_viewer(viewer_id.to_string(), cursor, limit)
            .await
    }

    /// Fetches a note for `viewer_id`, resolving their effective
    /// permission. A viewer with no ownership or share record gets
    /// `NotFound` rather than `Forbidden`, so the endpoint doesn't reveal
    /// that the note exists.
    pub async fn get(&self, note_id: &str, viewer_id: &str) -> Result<Note> {
        let mut n = self.repo.get_by_id(note_id.to_string()).await?;
        if n.owner_id == viewer_id {
            n.my_permission = Permission::Owner;
            return Ok(n);
        }
        let perm = self
            .repo
            .share_permission(note_id.to_string(), viewer_id.to_string())
            .await?;
        match perm {
            Some(p) => {
                n.my_permission = p;
                Ok(n)
            }
            None => Err(AppError::NotFound),
        }
    }

    pub async fn create(
        &self,
        owner_id: &str,
        title: String,
        content: String,
        mentioned_user_ids: Vec<String>,
    ) -> Result<Note> {
        if title.is_empty() {
            return Err(AppError::Validation("title is required".to_string()));
        }
        let n = self
            .repo
            .create(owner_id.to_string(), title, content)
            .await?;
        self.notify_mentions(&n, owner_id, None, &mentioned_user_ids)
            .await?;
        Ok(n)
    }

    pub async fn update(
        &self,
        note_id: &str,
        actor_id: &str,
        expected_version: i64,
        title: String,
        content: String,
        mentioned_user_ids: Vec<String>,
    ) -> Result<UpdateResult> {
        if title.is_empty() {
            return Err(AppError::Validation("title is required".to_string()));
        }
        let current = self.get(note_id, actor_id).await?;
        if current.my_permission != Permission::Owner && current.my_permission != Permission::Edit {
            return Err(AppError::Forbidden);
        }

        let existing = self.repo.existing_mentions(note_id.to_string()).await?;

        match self
            .repo
            .update(note_id.to_string(), title, content, expected_version)
            .await?
        {
            UpdateOutcome::Conflict(mut n) => {
                n.my_permission = current.my_permission;
                Ok(UpdateResult::Conflict(n))
            }
            UpdateOutcome::Updated(mut updated) => {
                updated.my_permission = current.my_permission;
                self.notify_mentions(&updated, actor_id, Some(&existing), &mentioned_user_ids)
                    .await?;
                Ok(UpdateResult::Updated(updated))
            }
        }
    }

    /// Records `mentioned_user_ids` against `n` and emails only the ones
    /// not already present in `already_mentioned`, so editing a note
    /// doesn't re-notify people mentioned in an earlier version.
    async fn notify_mentions(
        &self,
        n: &Note,
        actor_id: &str,
        already_mentioned: Option<&std::collections::HashSet<String>>,
        mentioned_user_ids: &[String],
    ) -> Result<()> {
        if mentioned_user_ids.is_empty() {
            return Ok(());
        }
        self.repo
            .add_mentions(n.id.clone(), mentioned_user_ids.to_vec())
            .await?;
        let actor = self.users.get_by_id(actor_id.to_string()).await?;
        let note_url = format!("{}/notes/{}", self.base_url, n.id);
        for uid in mentioned_user_ids {
            if already_mentioned.is_some_and(|s| s.contains(uid)) || uid == actor_id {
                continue;
            }
            // Unknown/invalid mention target: skip rather than fail the save.
            let Ok(mentioned) = self.users.get_by_id(uid.clone()).await else {
                continue;
            };
            self.mailer
                .notify_mentioned(
                    mentioned.email,
                    actor.display_name.clone(),
                    n.title.clone(),
                    note_url.clone(),
                )
                .await;
        }
        Ok(())
    }

    pub async fn delete(&self, note_id: &str, actor_id: &str) -> Result<()> {
        let n = self.repo.get_by_id(note_id.to_string()).await?;
        if n.owner_id != actor_id {
            return Err(AppError::Forbidden);
        }
        self.repo.delete(note_id.to_string()).await
    }

    async fn require_owner(&self, note_id: &str, actor_id: &str) -> Result<Note> {
        let n = self.repo.get_by_id(note_id.to_string()).await?;
        if n.owner_id != actor_id {
            return Err(AppError::Forbidden);
        }
        Ok(n)
    }

    pub async fn list_shares(
        &self,
        note_id: &str,
        actor_id: &str,
    ) -> Result<(Vec<UserShare>, Option<PublicShare>)> {
        self.require_owner(note_id, actor_id).await?;
        let shares = self.repo.list_shares(note_id.to_string()).await?;
        let public = self.repo.get_public_share(note_id.to_string()).await?;
        Ok((shares, public))
    }

    pub async fn share_with_user(
        &self,
        note_id: &str,
        actor_id: &str,
        target_user_id: &str,
        permission: Permission,
    ) -> Result<UserShare> {
        let n = self.require_owner(note_id, actor_id).await?;
        if target_user_id == actor_id {
            return Err(AppError::Validation(
                "cannot share a note with yourself".to_string(),
            ));
        }
        let target = self
            .users
            .get_by_id(target_user_id.to_string())
            .await
            .map_err(|_| AppError::Validation("unknown user".to_string()))?;
        self.repo
            .upsert_share(
                note_id.to_string(),
                target_user_id.to_string(),
                permission.clone(),
            )
            .await?;

        let actor = self.users.get_by_id(actor_id.to_string()).await?;
        let note_url = format!("{}/notes/{}", self.base_url, note_id);
        self.mailer
            .notify_note_shared(
                target.email,
                actor.display_name,
                n.title,
                note_url,
                permission == Permission::Edit,
            )
            .await;

        Ok(UserShare {
            user_id: target.id,
            display_name: target.display_name,
            avatar_url: String::new(),
            permission,
            created_at: Utc::now(),
        })
    }

    pub async fn revoke_share(
        &self,
        note_id: &str,
        actor_id: &str,
        target_user_id: &str,
    ) -> Result<()> {
        self.require_owner(note_id, actor_id).await?;
        self.repo
            .delete_share(note_id.to_string(), target_user_id.to_string())
            .await
    }

    pub async fn create_public_share(
        &self,
        note_id: &str,
        actor_id: &str,
    ) -> Result<(PublicShare, String)> {
        self.require_owner(note_id, actor_id).await?;
        let ps = self.repo.create_public_share(note_id.to_string()).await?;
        let url = format!("{}/shared/{}", self.base_url, ps.token);
        Ok((ps, url))
    }

    pub async fn revoke_public_share(&self, note_id: &str, actor_id: &str) -> Result<()> {
        self.require_owner(note_id, actor_id).await?;
        self.repo.delete_public_share(note_id.to_string()).await
    }

    pub async fn get_public_note(&self, token: &str) -> Result<PublicNoteView> {
        self.repo.get_by_public_token(token.to_string()).await
    }
}
