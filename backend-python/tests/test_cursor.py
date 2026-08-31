from datetime import UTC, datetime

import pytest

from app.notes.cursor import InvalidCursorError, decode_cursor, encode_cursor


def test_round_trip() -> None:
    dt = datetime(2024, 3, 1, 12, 30, 0, 123456, tzinfo=UTC)
    encoded = encode_cursor(dt, "note-1")
    decoded = decode_cursor(encoded)
    assert decoded.id == "note-1"
    assert decoded.updated_at == "2024-03-01T12:30:00.123456Z"


def test_encoded_cursor_has_no_padding() -> None:
    dt = datetime(2024, 3, 1, 12, 30, 0, 123456, tzinfo=UTC)
    encoded = encode_cursor(dt, "note-1")
    assert "=" not in encoded


@pytest.mark.parametrize("value", ["not-base64!!", "", "!!!"])
def test_invalid_cursor_rejected(value: str) -> None:
    with pytest.raises(InvalidCursorError):
        decode_cursor(value)
