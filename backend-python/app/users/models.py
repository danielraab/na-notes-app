from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass
class User:
    id: str
    email: str
    display_name: str
    avatar_url: str | None
    created_at: datetime


@dataclass
class UserSummary:
    id: str
    display_name: str
    avatar_url: str | None
