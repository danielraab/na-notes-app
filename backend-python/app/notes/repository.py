from __future__ import annotations

import uuid

from app import randtoken
from app.apperr import NotFoundError
from app.db import Database
from app.notes.cursor import decode_cursor, encode_cursor
from app.notes.models import Note, Page, Permission, PublicNoteView, PublicShare, Summary, UserShare
from app.timeutil import from_iso, now_utc, to_iso

_PUBLIC_SHARE_TOKEN_BYTES = 16  # 128 bits of entropy, see ADR 0009


class NoteRepository:
    def __init__(self, db: Database) -> None:
        self._db = db

    def create(self, owner_id: str, title: str, content: str) -> Note:
        now = now_utc()
        note_id = str(uuid.uuid4())
        with self._db.cursor() as cur:
            cur.execute(
                "INSERT INTO notes (id, owner_id, title, content_markdown, version, created_at, updated_at) "
                "VALUES (?, ?, ?, ?, 1, ?, ?)",
                (note_id, owner_id, title, content, to_iso(now), to_iso(now)),
            )
        return Note(
            id=note_id,
            owner_id=owner_id,
            title=title,
            content_markdown=content,
            version=1,
            is_public=False,
            created_at=now,
            updated_at=now,
            my_permission=Permission.OWNER,
        )

    def get_by_id(self, note_id: str) -> Note | None:
        """Fetches the raw note without regard to who is asking; callers
        (the service layer) are responsible for authorization decisions."""
        with self._db.cursor() as cur:
            row = cur.execute(
                "SELECT n.id, n.owner_id, n.title, n.content_markdown, n.version, "
                "n.created_at, n.updated_at, nps.note_id AS public_note_id "
                "FROM notes n "
                "LEFT JOIN note_public_shares nps ON nps.note_id = n.id "
                "WHERE n.id = ?",
                (note_id,),
            ).fetchone()
        if row is None:
            return None
        return Note(
            id=row["id"],
            owner_id=row["owner_id"],
            title=row["title"],
            content_markdown=row["content_markdown"],
            version=row["version"],
            is_public=row["public_note_id"] is not None,
            created_at=from_iso(row["created_at"]),
            updated_at=from_iso(row["updated_at"]),
            my_permission=Permission.READ,  # resolved by the service layer
        )

    def update(
        self, note_id: str, title: str, content: str, expected_version: int
    ) -> tuple[Note | None, bool]:
        """Applies an optimistic-concurrency-checked edit (ADR 0008): only
        succeeds if the row's current version still matches
        expected_version. Returns (note, conflict) — on conflict, note is
        the current server copy so the caller can hand it back in a 409.
        """
        now = to_iso(now_utc())
        with self._db.cursor() as cur:
            cur.execute(
                "UPDATE notes SET title = ?, content_markdown = ?, version = version + 1, updated_at = ? "
                "WHERE id = ? AND version = ?",
                (title, content, now, note_id, expected_version),
            )
            affected = cur.rowcount
        if affected == 0:
            current = self.get_by_id(note_id)
            if current is None:
                return None, False  # not found takes precedence over conflict
            return current, True
        return self.get_by_id(note_id), False

    def delete(self, note_id: str) -> bool:
        with self._db.cursor() as cur:
            cur.execute("DELETE FROM notes WHERE id = ?", (note_id,))
            return cur.rowcount > 0

    def share_permission(self, note_id: str, user_id: str) -> Permission | None:
        """The explicit share permission granted to user_id on note_id, if
        any. Does not consider ownership."""
        with self._db.cursor() as cur:
            row = cur.execute(
                "SELECT permission FROM note_shares WHERE note_id = ? AND user_id = ?",
                (note_id, user_id),
            ).fetchone()
        return Permission(row["permission"]) if row is not None else None

    def list_for_viewer(self, user_id: str, cursor: str | None, limit: int) -> Page:
        """A cursor page of notes owned by, or shared with, user_id,
        newest-edited first (ADR 0007)."""
        where_cursor = ""
        args: list[object] = [user_id, user_id, user_id, user_id]
        if cursor:
            c = decode_cursor(cursor)
            where_cursor = "AND (n.updated_at, n.id) < (?, ?)"
            args.extend([c.updated_at, c.id])
        args.append(limit + 1)

        query = f"""
            SELECT n.id, n.title, n.content_markdown, n.owner_id, n.updated_at,
                   CASE WHEN n.owner_id = ? THEN 'owner' ELSE ns.permission END AS permission,
                   CASE WHEN nps.note_id IS NOT NULL THEN 1 ELSE 0 END AS is_public
            FROM notes n
            LEFT JOIN note_shares ns ON ns.note_id = n.id AND ns.user_id = ?
            LEFT JOIN note_public_shares nps ON nps.note_id = n.id
            WHERE (n.owner_id = ? OR ns.user_id = ?)
            {where_cursor}
            ORDER BY n.updated_at DESC, n.id DESC
            LIMIT ?
        """
        with self._db.cursor() as cur:
            rows = cur.execute(query, args).fetchall()

        items = [
            Summary(
                id=row["id"],
                title=row["title"],
                content_markdown=row["content_markdown"],
                owner_id=row["owner_id"],
                my_permission=Permission(row["permission"]),
                is_public=bool(row["is_public"]),
                updated_at=from_iso(row["updated_at"]),
            )
            for row in rows
        ]

        page = Page(items=items)
        if len(items) > limit:
            page.items = items[:limit]
            last = page.items[-1]
            page.next_cursor = encode_cursor(last.updated_at, last.id)
        return page

    def list_shares(self, note_id: str) -> list[UserShare]:
        with self._db.cursor() as cur:
            rows = cur.execute(
                "SELECT u.id, u.display_name, u.avatar_url, ns.permission, ns.created_at "
                "FROM note_shares ns "
                "JOIN users u ON u.id = ns.user_id "
                "WHERE ns.note_id = ? "
                "ORDER BY ns.created_at",
                (note_id,),
            ).fetchall()
        return [
            UserShare(
                user_id=row["id"],
                display_name=row["display_name"],
                avatar_url=row["avatar_url"],
                permission=Permission(row["permission"]),
                created_at=from_iso(row["created_at"]),
            )
            for row in rows
        ]

    def upsert_share(self, note_id: str, user_id: str, permission: Permission) -> None:
        """Grants (or changes the permission of) user_id's access to
        note_id. The owner explicitly re-sharing an already-shared note
        still triggers a notification email (see NoteService.share_with_user)
        — a deliberate simplification over tracking new-vs-changed shares.
        """
        with self._db.cursor() as cur:
            cur.execute(
                "INSERT INTO note_shares (note_id, user_id, permission, created_at) VALUES (?, ?, ?, ?) "
                "ON CONFLICT(note_id, user_id) DO UPDATE SET permission = excluded.permission",
                (note_id, user_id, permission.value, to_iso(now_utc())),
            )

    def delete_share(self, note_id: str, user_id: str) -> bool:
        with self._db.cursor() as cur:
            cur.execute("DELETE FROM note_shares WHERE note_id = ? AND user_id = ?", (note_id, user_id))
            return cur.rowcount > 0

    def get_public_share(self, note_id: str) -> PublicShare | None:
        with self._db.cursor() as cur:
            row = cur.execute(
                "SELECT token, created_at FROM note_public_shares WHERE note_id = ?", (note_id,)
            ).fetchone()
        if row is None:
            return None
        return PublicShare(token=row["token"], created_at=from_iso(row["created_at"]))

    def create_public_share(self, note_id: str) -> PublicShare:
        """(Re)publishes note_id with a freshly generated, unguessable
        token, replacing any previous token (ADR 0009)."""
        token = randtoken.new(_PUBLIC_SHARE_TOKEN_BYTES)
        now = now_utc()
        with self._db.cursor() as cur:
            cur.execute(
                "INSERT INTO note_public_shares (note_id, token, created_at) VALUES (?, ?, ?) "
                "ON CONFLICT(note_id) DO UPDATE SET token = excluded.token, created_at = excluded.created_at",
                (note_id, token, to_iso(now)),
            )
        return PublicShare(token=token, created_at=now)

    def delete_public_share(self, note_id: str) -> bool:
        with self._db.cursor() as cur:
            cur.execute("DELETE FROM note_public_shares WHERE note_id = ?", (note_id,))
            return cur.rowcount > 0

    def get_by_public_token(self, token: str) -> PublicNoteView | None:
        with self._db.cursor() as cur:
            row = cur.execute(
                "SELECT n.title, n.content_markdown, n.updated_at "
                "FROM note_public_shares nps "
                "JOIN notes n ON n.id = nps.note_id "
                "WHERE nps.token = ?",
                (token,),
            ).fetchone()
        if row is None:
            return None
        return PublicNoteView(
            title=row["title"],
            content_markdown=row["content_markdown"],
            updated_at=from_iso(row["updated_at"]),
        )

    def existing_mentions(self, note_id: str) -> set[str]:
        """The set of user IDs already recorded as mentioned in note_id, so
        the caller can notify only newly added mentions."""
        with self._db.cursor() as cur:
            rows = cur.execute("SELECT user_id FROM note_mentions WHERE note_id = ?", (note_id,)).fetchall()
        return {row["user_id"] for row in rows}

    def add_mentions(self, note_id: str, user_ids: list[str]) -> None:
        now = to_iso(now_utc())
        with self._db.cursor() as cur:
            for user_id in user_ids:
                cur.execute(
                    "INSERT INTO note_mentions (note_id, user_id, created_at) VALUES (?, ?, ?) "
                    "ON CONFLICT DO NOTHING",
                    (note_id, user_id, now),
                )


def require_note(repo: NoteRepository, note_id: str) -> Note:
    note = repo.get_by_id(note_id)
    if note is None:
        raise NotFoundError(f"note {note_id} not found")
    return note
