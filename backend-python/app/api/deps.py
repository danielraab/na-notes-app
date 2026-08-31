"""FastAPI dependency wiring: pulls the app-wide services (config, stores,
services) off `request.app.state`, and resolves the current caller's
identity from the session context the middleware attaches to the request.
"""

from __future__ import annotations

from dataclasses import dataclass

from fastapi import Request

from app.api.errors import ApiError
from app.auth.oidc import OidcClient
from app.auth.store import AuthStore
from app.config import Config
from app.notes.service import NoteService
from app.users.repository import UserRepository


@dataclass
class AppState:
    config: Config
    auth_store: AuthStore
    oidc: OidcClient
    users: UserRepository
    notes: NoteService


def get_state(request: Request) -> AppState:
    return request.app.state.deps


def get_viewer_id_optional(request: Request) -> str | None:
    """ "" for an anonymous caller, or the resolved user ID — set by
    SessionContextMiddleware from the session cookie."""
    return getattr(request.state, "user_id", None)


def require_viewer_id(request: Request) -> str:
    user_id = getattr(request.state, "user_id", None)
    if not user_id:
        raise ApiError(401, "UNAUTHENTICATED", "login required")
    return user_id
