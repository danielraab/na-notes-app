"""Sentinel domain errors. HTTP concerns (status codes) are mapped from
these in app.api.errors — domain/service code never imports FastAPI/HTTP
status codes directly.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from app.notes.models import Note


class AppError(Exception):
    """Base class for domain errors the HTTP layer knows how to map."""


class NotFoundError(AppError):
    pass


class ForbiddenError(AppError):
    pass


class ValidationError(AppError):
    pass


class VersionConflictError(AppError):
    """Raised alongside the note's current server copy so the caller can
    hand it back to the client in the 409 response body (ADR 0008)."""

    def __init__(self, current: Note) -> None:
        super().__init__("note was modified since it was last loaded")
        self.current = current
