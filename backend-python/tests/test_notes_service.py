import pytest

from app.apperr import ForbiddenError, NotFoundError, ValidationError, VersionConflictError
from app.db import Database
from app.mail import Mailer
from app.notes.models import Permission
from app.notes.repository import NoteRepository
from app.notes.service import NoteService
from app.users.repository import UserRepository


class FakeMailer(Mailer):
    def __init__(self) -> None:
        self.shared: list[tuple] = []
        self.mentioned: list[tuple] = []

    def notify_note_shared(self, to, actor_name, note_title, note_url, editable) -> None:  # type: ignore[override]
        self.shared.append((to, actor_name, note_title, note_url, editable))

    def notify_mentioned(self, to, actor_name, note_title, note_url) -> None:  # type: ignore[override]
        self.mentioned.append((to, actor_name, note_title, note_url))


@pytest.fixture
def mailer() -> FakeMailer:
    return FakeMailer()


@pytest.fixture
def service(db: Database, mailer: FakeMailer) -> NoteService:
    return NoteService(NoteRepository(db), UserRepository(db), mailer, "http://localhost:5173")


@pytest.fixture
def users_repo(db: Database) -> UserRepository:
    return UserRepository(db)


def make_user(users_repo: UserRepository, subject: str):
    return users_repo.upsert_from_oidc(subject, f"{subject}@example.com", f"User {subject}", "")


def test_create_requires_title(service: NoteService, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")
    with pytest.raises(ValidationError):
        service.create(owner.id, "", "content", [])


def test_get_unshared_note_is_not_found_not_forbidden(
    service: NoteService, users_repo: UserRepository
) -> None:
    owner = make_user(users_repo, "owner")
    other = make_user(users_repo, "other")
    note = service.create(owner.id, "Title", "content", [])

    with pytest.raises(NotFoundError):
        service.get(note.id, other.id)


def test_update_requires_edit_permission(service: NoteService, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")
    reader = make_user(users_repo, "reader")
    note = service.create(owner.id, "Title", "content", [])
    service.share_with_user(note.id, owner.id, reader.id, Permission.READ)

    with pytest.raises(ForbiddenError):
        service.update(note.id, reader.id, note.version, "New title", "new content", [])


def test_update_version_conflict_carries_current_note(
    service: NoteService, users_repo: UserRepository
) -> None:
    owner = make_user(users_repo, "owner")
    note = service.create(owner.id, "Title", "content", [])
    service.update(note.id, owner.id, note.version, "First edit", "content", [])

    with pytest.raises(VersionConflictError) as exc_info:
        service.update(note.id, owner.id, note.version, "Stale edit", "content", [])

    assert exc_info.value.current.title == "First edit"


def test_mentions_notify_only_new_targets_on_update(
    service: NoteService, users_repo: UserRepository, mailer: FakeMailer
) -> None:
    owner = make_user(users_repo, "owner")
    alice = make_user(users_repo, "alice")
    bob = make_user(users_repo, "bob")

    note = service.create(owner.id, "Title", "hi @alice", [alice.id])
    assert len(mailer.mentioned) == 1
    assert mailer.mentioned[0][0] == "alice@example.com"

    service.update(note.id, owner.id, note.version, "Title", "hi @alice and @bob", [alice.id, bob.id])

    # alice was already notified; only bob (newly mentioned) gets a new email.
    assert len(mailer.mentioned) == 2
    assert mailer.mentioned[1][0] == "bob@example.com"


def test_mentioning_self_sends_no_email(
    service: NoteService, users_repo: UserRepository, mailer: FakeMailer
) -> None:
    owner = make_user(users_repo, "owner")
    service.create(owner.id, "Title", "note to self", [owner.id])
    assert mailer.mentioned == []


def test_share_with_self_is_rejected(service: NoteService, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")
    note = service.create(owner.id, "Title", "content", [])
    with pytest.raises(ValidationError):
        service.share_with_user(note.id, owner.id, owner.id, Permission.READ)


def test_only_owner_can_manage_sharing(service: NoteService, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")
    other = make_user(users_repo, "other")
    target = make_user(users_repo, "target")
    note = service.create(owner.id, "Title", "content", [])

    with pytest.raises(ForbiddenError):
        service.share_with_user(note.id, other.id, target.id, Permission.READ)
    with pytest.raises(ForbiddenError):
        service.create_public_share(note.id, other.id)


def test_delete_only_by_owner(service: NoteService, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")
    other = make_user(users_repo, "other")
    note = service.create(owner.id, "Title", "content", [])

    with pytest.raises(ForbiddenError):
        service.delete(note.id, other.id)


def test_public_note_view_hides_owner_identity(service: NoteService, users_repo: UserRepository) -> None:
    owner = make_user(users_repo, "owner")
    note = service.create(owner.id, "Public", "content", [])
    share, _ = service.create_public_share(note.id, owner.id)

    view = service.get_public_note(share.token)
    assert view.title == "Public"
    assert not hasattr(view, "owner_id")
