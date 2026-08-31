"""Owns the database connection and forward-only migrations. No other
module may open a database connection directly (see
/docs/adr/0006-sqlite-owned-by-backend.md and
/docs/adr/0013-exchangeable-database-backend.md).

This implementation only supports SQLite — see
docs/decisions/0002-sqlite-only.md for why PostgreSQL (opt-in per ADR
0013) was not added here.
"""

from __future__ import annotations

import sqlite3
import threading
from collections.abc import Iterator
from contextlib import contextmanager
from importlib import resources
from pathlib import Path

from app.timeutil import now_utc, to_iso


class UnsupportedDatabaseError(RuntimeError):
    pass


class Database:
    """Thin wrapper around a single sqlite3 connection, serialized behind
    a lock. SQLite is single-writer anyway (see ADR 0006's WAL-mode note);
    one shared connection plus a lock is simpler than a pool and gives the
    same effective concurrency as backend-go's `SetMaxOpenConns(1)`.
    """

    def __init__(self, conn: sqlite3.Connection) -> None:
        self._conn = conn
        self._lock = threading.RLock()

    @contextmanager
    def cursor(self) -> Iterator[sqlite3.Cursor]:
        with self._lock:
            cur = self._conn.cursor()
            try:
                yield cur
                self._conn.commit()
            except BaseException:
                self._conn.rollback()
                raise
            finally:
                cur.close()

    def close(self) -> None:
        with self._lock:
            self._conn.close()


def _resolve_sqlite_path(database_url: str) -> str:
    if database_url.startswith(("postgres://", "postgresql://")):
        raise UnsupportedDatabaseError(
            "DATABASE_URL requests PostgreSQL, which this implementation does not "
            "support — see backend-python/docs/decisions/0002-sqlite-only.md"
        )
    if database_url.startswith("sqlite://"):
        return database_url[len("sqlite://") :]
    if database_url.startswith("file:"):
        return database_url[len("file:") :]
    return database_url


def open_database(database_url: str) -> Database:
    """Opens (creating if necessary) the SQLite database identified by
    database_url and applies any migrations that haven't run yet.
    """
    path = _resolve_sqlite_path(database_url)
    if path != ":memory:":
        parent = Path(path).parent
        if str(parent) not in ("", "."):
            parent.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(path, check_same_thread=False, timeout=5.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA busy_timeout = 5000")

    db = Database(conn)
    _migrate(db)
    return db


def _migrate(db: Database) -> None:
    with db.cursor() as cur:
        cur.execute(
            "CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY, applied_at TEXT NOT NULL)"
        )
        applied = {row["name"] for row in cur.execute("SELECT name FROM schema_migrations")}

    migrations_dir = resources.files("app.migrations")
    names = sorted(entry.name for entry in migrations_dir.iterdir() if entry.name.endswith(".sql"))

    for name in names:
        if name in applied:
            continue
        sql = migrations_dir.joinpath(name).read_text(encoding="utf-8")
        with db.cursor() as cur:
            # Wrapped so a migration file's statements apply atomically —
            # SQLite DDL is transactional.
            cur.executescript(f"BEGIN;\n{sql}\nCOMMIT;")
            cur.execute(
                "INSERT INTO schema_migrations (name, applied_at) VALUES (?, ?)",
                (name, to_iso(now_utc())),
            )
