//! DTOs mirror the schemas in /openapi/openapi.yaml exactly (field names,
//! casing, nullability) — that file is the source of truth; if these
//! diverge from it, the spec is wrong or this code is.

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

use crate::notes;
use crate::users;

#[derive(Serialize)]
pub struct UserDto {
    pub id: String,
    pub email: String,
    #[serde(rename = "displayName")]
    pub display_name: String,
    #[serde(rename = "avatarUrl")]
    pub avatar_url: Option<String>,
}

pub fn to_user_dto(u: users::User) -> UserDto {
    UserDto {
        id: u.id,
        email: u.email,
        display_name: u.display_name,
        avatar_url: u.avatar_url,
    }
}

#[derive(Serialize)]
pub struct UserSummaryDto {
    pub id: String,
    #[serde(rename = "displayName")]
    pub display_name: String,
    #[serde(rename = "avatarUrl")]
    pub avatar_url: Option<String>,
}

pub fn to_user_summary_dto(u: users::Summary) -> UserSummaryDto {
    UserSummaryDto {
        id: u.id,
        display_name: u.display_name,
        avatar_url: u.avatar_url,
    }
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct NoteInputDto {
    // `default` on title/contentMarkdown too, matching backend-go: a
    // missing field decodes as "" rather than a request-body error, and
    // an empty title is rejected by notes::Service's own validation
    // ("title is required") rather than at the decode step.
    #[serde(default)]
    pub title: String,
    #[serde(rename = "contentMarkdown", default)]
    pub content_markdown: String,
    #[serde(rename = "mentionedUserIds", default)]
    pub mentioned_user_ids: Vec<String>,
}

#[derive(Serialize)]
pub struct NoteDto {
    pub id: String,
    pub title: String,
    #[serde(rename = "contentMarkdown")]
    pub content_markdown: String,
    #[serde(rename = "ownerId")]
    pub owner_id: String,
    pub version: i64,
    #[serde(rename = "myPermission")]
    pub my_permission: String,
    #[serde(rename = "isPublic")]
    pub is_public: bool,
    #[serde(rename = "createdAt")]
    pub created_at: DateTime<Utc>,
    #[serde(rename = "updatedAt")]
    pub updated_at: DateTime<Utc>,
}

pub fn to_note_dto(n: notes::Note) -> NoteDto {
    NoteDto {
        id: n.id,
        title: n.title,
        content_markdown: n.content_markdown,
        owner_id: n.owner_id,
        version: n.version,
        my_permission: n.my_permission.as_str().to_string(),
        is_public: n.is_public,
        created_at: n.created_at,
        updated_at: n.updated_at,
    }
}

#[derive(Serialize)]
pub struct NoteSummaryDto {
    pub id: String,
    pub title: String,
    #[serde(rename = "contentMarkdown")]
    pub content_markdown: String,
    #[serde(rename = "ownerId")]
    pub owner_id: String,
    #[serde(rename = "myPermission")]
    pub my_permission: String,
    #[serde(rename = "isPublic")]
    pub is_public: bool,
    #[serde(rename = "updatedAt")]
    pub updated_at: DateTime<Utc>,
}

pub fn to_note_summary_dto(s: notes::Summary) -> NoteSummaryDto {
    NoteSummaryDto {
        id: s.id,
        title: s.title,
        content_markdown: s.content_markdown,
        owner_id: s.owner_id,
        my_permission: s.my_permission.as_str().to_string(),
        is_public: s.is_public,
        updated_at: s.updated_at,
    }
}

#[derive(Serialize)]
pub struct NotePageDto {
    pub items: Vec<NoteSummaryDto>,
    #[serde(rename = "nextCursor")]
    pub next_cursor: Option<String>,
}

pub fn to_note_page_dto(p: notes::Page) -> NotePageDto {
    NotePageDto {
        items: p.items.into_iter().map(to_note_summary_dto).collect(),
        next_cursor: if p.next_cursor.is_empty() {
            None
        } else {
            Some(p.next_cursor)
        },
    }
}

#[derive(Serialize)]
pub struct UserShareDto {
    pub user: UserSummaryDto,
    pub permission: String,
    #[serde(rename = "createdAt")]
    pub created_at: DateTime<Utc>,
}

pub fn to_user_share_dto(s: notes::UserShare) -> UserShareDto {
    UserShareDto {
        user: UserSummaryDto {
            id: s.user_id,
            display_name: s.display_name,
            avatar_url: if s.avatar_url.is_empty() {
                None
            } else {
                Some(s.avatar_url)
            },
        },
        permission: s.permission.as_str().to_string(),
        created_at: s.created_at,
    }
}

#[derive(Serialize)]
pub struct PublicShareDto {
    pub token: String,
    pub url: String,
    #[serde(rename = "createdAt")]
    pub created_at: DateTime<Utc>,
}

pub fn to_public_share_dto(ps: notes::PublicShare, url: String) -> PublicShareDto {
    PublicShareDto {
        token: ps.token,
        url,
        created_at: ps.created_at,
    }
}

#[derive(Serialize)]
pub struct PublicNoteViewDto {
    pub title: String,
    #[serde(rename = "contentMarkdown")]
    pub content_markdown: String,
    #[serde(rename = "updatedAt")]
    pub updated_at: DateTime<Utc>,
}

pub fn to_public_note_view_dto(v: notes::PublicNoteView) -> PublicNoteViewDto {
    PublicNoteViewDto {
        title: v.title,
        content_markdown: v.content_markdown,
        updated_at: v.updated_at,
    }
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateShareDto {
    #[serde(rename = "userId", default)]
    pub user_id: String,
    #[serde(default)]
    pub permission: String,
}

#[derive(Serialize)]
pub struct NoteSharesDto {
    #[serde(rename = "userShares")]
    pub user_shares: Vec<UserShareDto>,
    #[serde(rename = "publicShare")]
    pub public_share: Option<PublicShareDto>,
}
