"""Timestamp helpers. Every stored/returned timestamp is UTC, ISO 8601
with microsecond precision and a trailing 'Z' — a fixed-width string
representation so lexicographic ordering matches chronological ordering
(relied on by the notes cursor, see app/notes/cursor.py).
"""

from __future__ import annotations

from datetime import UTC, datetime


def now_utc() -> datetime:
    return datetime.now(UTC)


def to_iso(dt: datetime) -> str:
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC).isoformat(timespec="microseconds").replace("+00:00", "Z")


def from_iso(s: str) -> datetime:
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    return datetime.fromisoformat(s)
