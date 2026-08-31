"""User accounts. Accounts are created lazily on first successful OIDC
login; there is no separate registration flow.
"""

from __future__ import annotations

import uuid

from app.db import Database
from app.timeutil import from_iso, now_utc, to_iso
from app.users.models import User, UserSummary


class UserRepository:
    def __init__(self, db: Database) -> None:
        self._db = db

    def upsert_from_oidc(self, subject: str, email: str, display_name: str, avatar_url: str) -> User:
        """Creates the user on first login, or refreshes their profile
        fields (display name/avatar can change at the identity provider)
        on subsequent logins. Matching happens on the stable OIDC subject,
        never on email alone, since some providers allow email reuse/change.
        """
        with self._db.cursor() as cur:
            row = cur.execute(
                "SELECT id, email, display_name, avatar_url, created_at FROM users WHERE oidc_subject = ?",
                (subject,),
            ).fetchone()

            if row is None:
                user_id = str(uuid.uuid4())
                created_at = now_utc()
                cur.execute(
                    "INSERT INTO users (id, oidc_subject, email, display_name, avatar_url, created_at) "
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    (user_id, subject, email, display_name, avatar_url or None, to_iso(created_at)),
                )
                return User(
                    id=user_id,
                    email=email,
                    display_name=display_name,
                    avatar_url=avatar_url or None,
                    created_at=created_at,
                )

            cur.execute(
                "UPDATE users SET email = ?, display_name = ?, avatar_url = ? WHERE id = ?",
                (email, display_name, avatar_url or None, row["id"]),
            )
            return User(
                id=row["id"],
                email=email,
                display_name=display_name,
                avatar_url=avatar_url or None,
                created_at=from_iso(row["created_at"]),
            )

    def get_by_id(self, user_id: str) -> User | None:
        with self._db.cursor() as cur:
            row = cur.execute(
                "SELECT id, email, display_name, avatar_url, created_at FROM users WHERE id = ?",
                (user_id,),
            ).fetchone()
        if row is None:
            return None
        return User(
            id=row["id"],
            email=row["email"],
            display_name=row["display_name"],
            avatar_url=row["avatar_url"],
            created_at=from_iso(row["created_at"]),
        )

    def search(self, exclude_user_id: str, query: str, limit: int) -> list[UserSummary]:
        """Users whose display name or email starts with query, excluding
        the caller, for mention/share autocomplete.
        """
        like = query.lower() + "%"
        with self._db.cursor() as cur:
            rows = cur.execute(
                "SELECT id, display_name, avatar_url FROM users "
                "WHERE id != ? AND (LOWER(display_name) LIKE ? OR LOWER(email) LIKE ?) "
                "ORDER BY display_name LIMIT ?",
                (exclude_user_id, like, like, limit),
            ).fetchall()
        return [
            UserSummary(id=r["id"], display_name=r["display_name"], avatar_url=r["avatar_url"]) for r in rows
        ]
