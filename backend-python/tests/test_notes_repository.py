import time

import pytest

from app.db import Database
from app.notes.models import Permission
from app.notes.repository import NoteRepository
from app.users.repository import UserRepository


@pytest.fixture
def notes_repo(db: Database) -> NoteRepository:
    return NoteRepository(db)


@pytest.fixture
def users_repo(db: Database) -> UserRepository:
    return UserRepository(db)


def make_user(users_repo: UserRepository, subject: str):
    return users_repo.upsert_from_oidc(subject, f"{subject}@example.com", f"User {subject}", "")


def test_note_lifecycle(notes_repo: NoteRepository, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")

    note = notes_repo.create(owner.id, "Title", "Some **content**")
    assert note.version == 1

    fetched = notes_repo.get_by_id(note.id)
    assert fetched is not None
    assert fetched.title == "Title"
    assert fetched.owner_id == owner.id

    updated, conflict = notes_repo.update(note.id, "New title", "New content", note.version)
    assert not conflict
    assert updated is not None
    assert updated.version == 2
    assert updated.title == "New title"

    stale, conflict = notes_repo.update(note.id, "Stale write", "x", 1)
    assert conflict
    assert stale is not None  # current server copy is returned alongside the conflict

    assert notes_repo.delete(note.id)
    assert notes_repo.get_by_id(note.id) is None


def test_sharing_visibility(notes_repo: NoteRepository, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")
    other = make_user(users_repo, "other")

    note = notes_repo.create(owner.id, "Private", "content")

    assert notes_repo.share_permission(note.id, other.id) is None

    page = notes_repo.list_for_viewer(other.id, None, 12)
    assert page.items == []

    notes_repo.upsert_share(note.id, other.id, Permission.READ)

    assert notes_repo.share_permission(note.id, other.id) == Permission.READ

    page = notes_repo.list_for_viewer(other.id, None, 12)
    assert len(page.items) == 1
    assert page.items[0].my_permission == Permission.READ

    assert notes_repo.delete_share(note.id, other.id)
    assert notes_repo.share_permission(note.id, other.id) is None


def test_public_share_uses_unguessable_token(notes_repo: NoteRepository, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")
    note = notes_repo.create(owner.id, "Public note", "hello world")

    share = notes_repo.create_public_share(note.id)
    assert len(share.token) >= 20
    assert share.token != note.id

    view = notes_repo.get_by_public_token(share.token)
    assert view is not None
    assert view.title == "Public note"

    assert notes_repo.delete_public_share(note.id)
    assert notes_repo.get_by_public_token(share.token) is None


def test_mentions_are_notified_only_once(notes_repo: NoteRepository, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")
    mentioned = make_user(users_repo, "mentioned")

    note = notes_repo.create(owner.id, "Note", "hi @mentioned")

    assert notes_repo.existing_mentions(note.id) == set()

    notes_repo.add_mentions(note.id, [mentioned.id])
    assert notes_repo.existing_mentions(note.id) == {mentioned.id}

    # Adding the same mention again must stay idempotent (no duplicate row/error).
    notes_repo.add_mentions(note.id, [mentioned.id])
    assert notes_repo.existing_mentions(note.id) == {mentioned.id}


def test_list_for_viewer_returns_full_markdown(
    notes_repo: NoteRepository, users_repo: UserRepository
) -> None:
    owner = make_user(users_repo, "owner")
    body = "# Heading\n\nSome **bold** text and a [link](https://example.com)\n\n- one\n- two"
    notes_repo.create(owner.id, "Note", body)

    page = notes_repo.list_for_viewer(owner.id, None, 10)
    assert len(page.items) == 1
    assert page.items[0].content_markdown == body


def test_list_for_viewer_cursor_pagination(notes_repo: NoteRepository, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")

    total = 5
    ids = set()
    for _ in range(total):
        note = notes_repo.create(owner.id, "Note", "content")
        ids.add(note.id)
        time.sleep(0.001)  # ensure distinct updated_at ordering

    seen: set[str] = set()
    cursor = None
    for _ in range(total + 1):
        page = notes_repo.list_for_viewer(owner.id, cursor, 2)
        for item in page.items:
            assert item.id not in seen, f"note {item.id} returned twice across pages"
            seen.add(item.id)
        if not page.next_cursor:
            break
        cursor = page.next_cursor
    else:
        pytest.fail("pagination did not terminate")

    assert seen == ids
