from __future__ import annotations

from app.apperr import ForbiddenError, NotFoundError, ValidationError, VersionConflictError
from app.mail import Mailer
from app.notes.models import Note, Page, Permission, PublicNoteView, PublicShare, Summary, UserShare
from app.notes.repository import NoteRepository
from app.timeutil import now_utc
from app.users.repository import UserRepository

INITIAL_PAGE_SIZE = 12

_SAMPLE_NOTE_ID = "00000000-0000-0000-0000-000000000000"


def _sample_note() -> Summary:
    return Summary(
        id=_SAMPLE_NOTE_ID,
        title="Welcome to NA Notes",
        content_markdown=(
            "Sign in to create your own notes, share them with teammates, and mention people to loop them in."
        ),
        owner_id=_SAMPLE_NOTE_ID,
        my_permission=Permission.READ,
        is_public=True,
        updated_at=now_utc(),
    )


class NoteService:
    def __init__(
        self, repo: NoteRepository, users: UserRepository, mailer: Mailer, frontend_base_url: str
    ) -> None:
        self._repo = repo
        self._users = users
        self._mailer = mailer
        self._base_url = frontend_base_url.rstrip("/")

    def list_notes(self, viewer_id: str | None, cursor: str | None, limit: int) -> Page:
        """The dashboard feed. An anonymous viewer always sees exactly the
        sample note, per the product spec."""
        if not viewer_id:
            return Page(items=[_sample_note()])
        if limit <= 0:
            limit = INITIAL_PAGE_SIZE
        return self._repo.list_for_viewer(viewer_id, cursor, limit)

    def get(self, note_id: str, viewer_id: str) -> Note:
        """Fetches a note for viewer_id, resolving their effective
        permission. A viewer with no ownership or share record gets
        NotFoundError rather than ForbiddenError, so the endpoint doesn't
        reveal that the note exists."""
        note = self._repo.get_by_id(note_id)
        if note is None:
            raise NotFoundError(f"note {note_id} not found")
        if note.owner_id == viewer_id:
            note.my_permission = Permission.OWNER
            return note
        permission = self._repo.share_permission(note_id, viewer_id)
        if permission is None:
            raise NotFoundError(f"note {note_id} not found")
        note.my_permission = permission
        return note

    def create(self, owner_id: str, title: str, content: str, mentioned_user_ids: list[str]) -> Note:
        if not title:
            raise ValidationError("title is required")
        note = self._repo.create(owner_id, title, content)
        self._notify_mentions(note, owner_id, set(), mentioned_user_ids)
        return note

    def update(
        self,
        note_id: str,
        actor_id: str,
        expected_version: int,
        title: str,
        content: str,
        mentioned_user_ids: list[str],
    ) -> Note:
        if not title:
            raise ValidationError("title is required")
        current = self.get(note_id, actor_id)
        if current.my_permission not in (Permission.OWNER, Permission.EDIT):
            raise ForbiddenError("not permitted to edit this note")

        existing = self._repo.existing_mentions(note_id)

        updated, conflict = self._repo.update(note_id, title, content, expected_version)
        if updated is None:
            raise NotFoundError(f"note {note_id} not found")
        updated.my_permission = current.my_permission
        if conflict:
            raise VersionConflictError(updated)

        self._notify_mentions(updated, actor_id, existing, mentioned_user_ids)
        return updated

    def _notify_mentions(
        self, note: Note, actor_id: str, already_mentioned: set[str], mentioned_user_ids: list[str]
    ) -> None:
        """Records mentioned_user_ids against note.id and emails only the
        ones not already present in already_mentioned, so editing a note
        doesn't re-notify people mentioned in an earlier version."""
        if not mentioned_user_ids:
            return
        self._repo.add_mentions(note.id, mentioned_user_ids)
        actor = self._users.get_by_id(actor_id)
        if actor is None:
            return
        note_url = f"{self._base_url}/notes/{note.id}"
        for user_id in mentioned_user_ids:
            if user_id in already_mentioned or user_id == actor_id:
                continue
            mentioned = self._users.get_by_id(user_id)
            if mentioned is None:
                continue  # unknown/invalid mention target: skip rather than fail the save
            self._mailer.notify_mentioned(mentioned.email, actor.display_name, note.title, note_url)

    def delete(self, note_id: str, actor_id: str) -> None:
        note = self._repo.get_by_id(note_id)
        if note is None:
            raise NotFoundError(f"note {note_id} not found")
        if note.owner_id != actor_id:
            raise ForbiddenError("not permitted to delete this note")
        self._repo.delete(note_id)

    def _require_owner(self, note_id: str, actor_id: str) -> Note:
        note = self._repo.get_by_id(note_id)
        if note is None:
            raise NotFoundError(f"note {note_id} not found")
        if note.owner_id != actor_id:
            raise ForbiddenError("not permitted to manage sharing on this note")
        return note

    def list_shares(self, note_id: str, actor_id: str) -> tuple[list[UserShare], PublicShare | None]:
        self._require_owner(note_id, actor_id)
        shares = self._repo.list_shares(note_id)
        public = self._repo.get_public_share(note_id)
        return shares, public

    def share_with_user(
        self, note_id: str, actor_id: str, target_user_id: str, permission: Permission
    ) -> UserShare:
        note = self._require_owner(note_id, actor_id)
        if target_user_id == actor_id:
            raise ValidationError("cannot share a note with yourself")
        target = self._users.get_by_id(target_user_id)
        if target is None:
            raise ValidationError("unknown user")
        self._repo.upsert_share(note_id, target_user_id, permission)

        actor = self._users.get_by_id(actor_id)
        if actor is not None:
            note_url = f"{self._base_url}/notes/{note_id}"
            self._mailer.notify_note_shared(
                target.email, actor.display_name, note.title, note_url, permission == Permission.EDIT
            )

        return UserShare(
            user_id=target.id,
            display_name=target.display_name,
            avatar_url=target.avatar_url,
            permission=permission,
            created_at=now_utc(),
        )

    def revoke_share(self, note_id: str, actor_id: str, target_user_id: str) -> None:
        self._require_owner(note_id, actor_id)
        if not self._repo.delete_share(note_id, target_user_id):
            raise NotFoundError("share not found")

    def create_public_share(self, note_id: str, actor_id: str) -> tuple[PublicShare, str]:
        self._require_owner(note_id, actor_id)
        share = self._repo.create_public_share(note_id)
        url = f"{self._base_url}/shared/{share.token}"
        return share, url

    def revoke_public_share(self, note_id: str, actor_id: str) -> None:
        self._require_owner(note_id, actor_id)
        if not self._repo.delete_public_share(note_id):
            raise NotFoundError("public share not found")

    def get_public_note(self, token: str) -> PublicNoteView:
        view = self._repo.get_by_public_token(token)
        if view is None:
            raise NotFoundError("public note not found")
        return view
