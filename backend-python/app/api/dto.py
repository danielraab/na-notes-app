"""Request/response models mirroring the schemas in
/openapi/openapi.yaml exactly (field names, casing, nullability) — that
file is the source of truth; if these diverge from it, the spec is wrong
or this code is.
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict

from app.notes.models import Note, Page, PublicNoteView, PublicShare, Summary, UserShare
from app.timeutil import to_iso
from app.users.models import User, UserSummary


def _to_camel(name: str) -> str:
    head, *tail = name.split("_")
    return head + "".join(part.title() for part in tail)


class CamelIn(BaseModel):
    """Base for request bodies: accepts only the exact camelCase field
    names the contract defines (mirrors Go's DisallowUnknownFields)."""

    model_config = ConfigDict(alias_generator=_to_camel, extra="forbid")


class CamelOut(BaseModel):
    """Base for response bodies."""

    model_config = ConfigDict(alias_generator=_to_camel, populate_by_name=True)


# ---- users ----------------------------------------------------------------


class UserDTO(CamelOut):
    id: str
    email: str
    display_name: str
    avatar_url: str | None


def user_to_dto(u: User) -> UserDTO:
    return UserDTO(id=u.id, email=u.email, display_name=u.display_name, avatar_url=u.avatar_url)


class UserSummaryDTO(CamelOut):
    id: str
    display_name: str
    avatar_url: str | None


def user_summary_to_dto(u: UserSummary) -> UserSummaryDTO:
    return UserSummaryDTO(id=u.id, display_name=u.display_name, avatar_url=u.avatar_url)


# ---- notes ------------------------------------------------------------------


class NoteInputDTO(CamelIn):
    title: str
    content_markdown: str
    mentioned_user_ids: list[str] = []


class NoteDTO(CamelOut):
    id: str
    title: str
    content_markdown: str
    owner_id: str
    version: int
    my_permission: str
    is_public: bool
    created_at: str
    updated_at: str


def note_to_dto(n: Note) -> NoteDTO:
    return NoteDTO(
        id=n.id,
        title=n.title,
        content_markdown=n.content_markdown,
        owner_id=n.owner_id,
        version=n.version,
        my_permission=n.my_permission.value,
        is_public=n.is_public,
        created_at=to_iso(n.created_at),
        updated_at=to_iso(n.updated_at),
    )


class NoteSummaryDTO(CamelOut):
    id: str
    title: str
    content_markdown: str
    owner_id: str
    my_permission: str
    is_public: bool
    updated_at: str


def note_summary_to_dto(s: Summary) -> NoteSummaryDTO:
    return NoteSummaryDTO(
        id=s.id,
        title=s.title,
        content_markdown=s.content_markdown,
        owner_id=s.owner_id,
        my_permission=s.my_permission.value,
        is_public=s.is_public,
        updated_at=to_iso(s.updated_at),
    )


class NotePageDTO(CamelOut):
    items: list[NoteSummaryDTO]
    next_cursor: str | None


def note_page_to_dto(page: Page) -> NotePageDTO:
    return NotePageDTO(items=[note_summary_to_dto(s) for s in page.items], next_cursor=page.next_cursor)


# ---- sharing ----------------------------------------------------------------


class ShareCreateDTO(CamelIn):
    user_id: str
    permission: str


class UserShareDTO(CamelOut):
    user: UserSummaryDTO
    permission: str
    created_at: str


def user_share_to_dto(s: UserShare) -> UserShareDTO:
    return UserShareDTO(
        user=UserSummaryDTO(id=s.user_id, display_name=s.display_name, avatar_url=s.avatar_url),
        permission=s.permission.value,
        created_at=to_iso(s.created_at),
    )


class PublicShareDTO(CamelOut):
    token: str
    url: str
    created_at: str


def public_share_to_dto(ps: PublicShare, url: str) -> PublicShareDTO:
    return PublicShareDTO(token=ps.token, url=url, created_at=to_iso(ps.created_at))


class ListSharesDTO(CamelOut):
    user_shares: list[UserShareDTO]
    public_share: PublicShareDTO | None


class PublicNoteViewDTO(CamelOut):
    title: str
    content_markdown: str
    updated_at: str


def public_note_view_to_dto(v: PublicNoteView) -> PublicNoteViewDTO:
    return PublicNoteViewDTO(
        title=v.title, content_markdown=v.content_markdown, updated_at=to_iso(v.updated_at)
    )
