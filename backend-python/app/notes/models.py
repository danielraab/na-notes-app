from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import StrEnum


class Permission(StrEnum):
    OWNER = "owner"
    EDIT = "edit"
    READ = "read"


@dataclass
class Note:
    id: str
    owner_id: str
    title: str
    content_markdown: str
    version: int
    is_public: bool
    created_at: datetime
    updated_at: datetime
    my_permission: Permission


@dataclass
class Summary:
    id: str
    title: str
    content_markdown: str
    owner_id: str
    my_permission: Permission
    is_public: bool
    updated_at: datetime


@dataclass
class Page:
    items: list[Summary] = field(default_factory=list)
    next_cursor: str | None = None


@dataclass
class UserShare:
    user_id: str
    display_name: str
    avatar_url: str | None
    permission: Permission
    created_at: datetime


@dataclass
class PublicShare:
    token: str
    created_at: datetime


@dataclass
class PublicNoteView:
    title: str
    content_markdown: str
    updated_at: datetime
