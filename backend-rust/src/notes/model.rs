use chrono::{DateTime, Utc};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Permission {
    Owner,
    Edit,
    Read,
}

impl Permission {
    pub fn as_str(&self) -> &'static str {
        match self {
            Permission::Owner => "owner",
            Permission::Edit => "edit",
            Permission::Read => "read",
        }
    }

    pub fn parse(s: &str) -> Option<Permission> {
        match s {
            "owner" => Some(Permission::Owner),
            "edit" => Some(Permission::Edit),
            "read" => Some(Permission::Read),
            _ => None,
        }
    }
}

#[derive(Debug, Clone)]
pub struct Note {
    pub id: String,
    pub owner_id: String,
    pub title: String,
    pub content_markdown: String,
    pub version: i64,
    pub is_public: bool,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub my_permission: Permission,
}

#[derive(Debug, Clone)]
pub struct Summary {
    pub id: String,
    pub title: String,
    pub content_markdown: String,
    pub owner_id: String,
    pub my_permission: Permission,
    pub is_public: bool,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Default)]
pub struct Page {
    pub items: Vec<Summary>,
    /// Empty means no more pages.
    pub next_cursor: String,
}

#[derive(Debug, Clone)]
pub struct UserShare {
    pub user_id: String,
    pub display_name: String,
    pub avatar_url: String,
    pub permission: Permission,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone)]
pub struct PublicShare {
    pub token: String,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone)]
pub struct PublicNoteView {
    pub title: String,
    pub content_markdown: String,
    pub updated_at: DateTime<Utc>,
}
