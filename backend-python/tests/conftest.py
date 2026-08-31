import tempfile
from collections.abc import Iterator
from pathlib import Path

import pytest

from app.db import Database, open_database


@pytest.fixture
def db() -> Iterator[Database]:
    with tempfile.TemporaryDirectory() as tmp:
        database = open_database(str(Path(tmp) / "test.db"))
        try:
            yield database
        finally:
            database.close()
