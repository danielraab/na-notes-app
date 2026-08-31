"""Encodes/decodes the opaque cursor used to page through the notes feed
(ADR 0007): a stable sort key of (updated_at, id). Opaque to clients; only
this module constructs or interprets it.
"""

from __future__ import annotations

import base64
import json
from dataclasses import dataclass
from datetime import datetime

from app.timeutil import to_iso


class InvalidCursorError(ValueError):
    pass


@dataclass
class Cursor:
    updated_at: str
    id: str


def encode_cursor(updated_at: datetime, note_id: str) -> str:
    payload = json.dumps({"u": to_iso(updated_at), "i": note_id}, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(payload).rstrip(b"=").decode("ascii")


def decode_cursor(value: str) -> Cursor:
    try:
        padded = value + "=" * (-len(value) % 4)
        payload = json.loads(base64.urlsafe_b64decode(padded))
        updated_at = payload["u"]
        note_id = payload["i"]
    except Exception as exc:
        raise InvalidCursorError(f"invalid cursor: {exc}") from exc
    if not updated_at or not note_id:
        raise InvalidCursorError("invalid cursor")
    return Cursor(updated_at=updated_at, id=note_id)
