from app.db import Database
from app.users.repository import UserRepository


def test_upsert_and_get_by_id_round_trips_created_at(db: Database) -> None:
    repo = UserRepository(db)

    created = repo.upsert_from_oidc("subject-1", "alice@example.com", "Alice", "")
    assert created.created_at is not None

    fetched = repo.get_by_id(created.id)
    assert fetched is not None
    assert fetched.id == created.id
    assert fetched.email == "alice@example.com"
    assert fetched.created_at == created.created_at

    # Second login with the same subject updates the profile rather than
    # creating a second account, and must still round-trip created_at.
    updated = repo.upsert_from_oidc("subject-1", "alice2@example.com", "Alice Updated", "")
    assert updated.id == created.id
    assert updated.email == "alice2@example.com"
    assert updated.display_name == "Alice Updated"
    assert updated.created_at == created.created_at


def test_search_excludes_caller(db: Database) -> None:
    repo = UserRepository(db)

    me = repo.upsert_from_oidc("me", "me@example.com", "Me", "")
    repo.upsert_from_oidc("alice", "alice@example.com", "Alice", "")

    results = repo.search(me.id, "Al", 10)
    assert len(results) == 1
    assert results[0].display_name == "Alice"

    results = repo.search(me.id, "Me", 10)
    assert results == []
