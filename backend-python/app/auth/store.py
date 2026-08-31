"""The server-side session the OIDC login flow creates (ADR 0004), plus
short-lived, server-side storage for in-flight logins (state + PKCE
verifier) keyed by the OAuth `state`.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta

from app import randtoken
from app.db import Database
from app.timeutil import from_iso, now_utc, to_iso

_SESSION_ID_BYTES = 32
_CSRF_TOKEN_BYTES = 32
_SESSION_TTL = timedelta(days=7)
_OIDC_REQUEST_TTL = timedelta(minutes=10)
_OIDC_STATE_BYTES = 24
_CODE_VERIFIER_BYTES = 32


@dataclass
class Session:
    id: str
    user_id: str
    csrf_token: str
    expires_at: datetime


@dataclass
class OidcRequest:
    state: str
    code_verifier: str
    redirect_to: str


class AuthStore:
    def __init__(self, db: Database) -> None:
        self._db = db

    def create_session(self, user_id: str) -> Session:
        session_id = randtoken.new(_SESSION_ID_BYTES)
        csrf_token = randtoken.new(_CSRF_TOKEN_BYTES)
        now = now_utc()
        expires_at = now + _SESSION_TTL
        with self._db.cursor() as cur:
            cur.execute(
                "INSERT INTO sessions (id, user_id, csrf_token, expires_at, created_at) "
                "VALUES (?, ?, ?, ?, ?)",
                (session_id, user_id, csrf_token, to_iso(expires_at), to_iso(now)),
            )
        return Session(id=session_id, user_id=user_id, csrf_token=csrf_token, expires_at=expires_at)

    def get_session(self, session_id: str) -> Session | None:
        with self._db.cursor() as cur:
            row = cur.execute(
                "SELECT id, user_id, csrf_token, expires_at FROM sessions WHERE id = ?",
                (session_id,),
            ).fetchone()
        if row is None:
            return None
        expires_at = from_iso(row["expires_at"])
        if now_utc() > expires_at:
            self.delete_session(session_id)
            return None
        return Session(
            id=row["id"], user_id=row["user_id"], csrf_token=row["csrf_token"], expires_at=expires_at
        )

    def delete_session(self, session_id: str) -> None:
        with self._db.cursor() as cur:
            cur.execute("DELETE FROM sessions WHERE id = ?", (session_id,))

    def create_oidc_request(self, redirect_to: str) -> OidcRequest:
        """Starts a login attempt. Also opportunistically clears expired
        requests, since they're otherwise never cleaned up (abandoned
        logins are the only source of them, and volume is low)."""
        now = now_utc()
        with self._db.cursor() as cur:
            cur.execute("DELETE FROM oidc_requests WHERE expires_at < ?", (to_iso(now),))

        state = randtoken.new(_OIDC_STATE_BYTES)
        code_verifier = randtoken.new(_CODE_VERIFIER_BYTES)
        expires_at = now + _OIDC_REQUEST_TTL
        with self._db.cursor() as cur:
            cur.execute(
                "INSERT INTO oidc_requests (state, code_verifier, redirect_to, expires_at) "
                "VALUES (?, ?, ?, ?)",
                (state, code_verifier, redirect_to, to_iso(expires_at)),
            )
        return OidcRequest(state=state, code_verifier=code_verifier, redirect_to=redirect_to)

    def consume_oidc_request(self, state: str) -> OidcRequest | None:
        """Looks up and deletes the request in one step: a state value
        must only ever be usable once."""
        with self._db.cursor() as cur:
            row = cur.execute(
                "SELECT state, code_verifier, redirect_to, expires_at FROM oidc_requests WHERE state = ?",
                (state,),
            ).fetchone()
            if row is None:
                return None
            cur.execute("DELETE FROM oidc_requests WHERE state = ?", (state,))

        if now_utc() > from_iso(row["expires_at"]):
            return None
        return OidcRequest(
            state=row["state"], code_verifier=row["code_verifier"], redirect_to=row["redirect_to"]
        )
