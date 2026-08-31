from urllib.parse import parse_qs, urlparse

import pytest
from starlette.testclient import TestClient

from app.api.cookies import CSRF_COOKIE_NAME, CSRF_HEADER_NAME, SESSION_COOKIE_NAME
from app.api.deps import AppState
from app.app import create_app
from app.auth.oidc import Claims
from app.auth.store import AuthStore
from app.config import Config
from app.db import Database
from app.mail import Mailer
from app.notes.repository import NoteRepository
from app.notes.service import NoteService
from app.users.repository import UserRepository


class FakeOidc:
    """Stands in for a real OIDC provider: auth_code_url/exchange without
    any network access, so these tests exercise our own login/callback/
    session/CSRF code without depending on a live identity provider."""

    def auth_code_url(self, state: str, code_verifier: str) -> str:
        return f"https://provider.example.com/authorize?state={state}&code_verifier_len={len(code_verifier)}"

    def exchange(self, code: str, code_verifier: str) -> Claims:
        return Claims(subject=f"sub-{code}", email=f"{code}@example.com", display_name=code, avatar_url="")


def build_config(**overrides) -> Config:
    defaults = dict(
        listen_host="0.0.0.0",
        listen_port=8080,
        public_base_url="http://localhost:8080",
        frontend_url="http://localhost:5173",
        allowed_origins=["http://localhost:5173"],
        session_secret="test-secret",
        cookie_domain="",
        oidc_issuer_url="https://issuer.example.com",
        oidc_client_id="client",
        oidc_client_secret="secret",
        oidc_redirect_url="http://localhost:8080/api/auth/callback",
        oidc_scopes=["openid", "profile", "email"],
        database_url="ignored",
        smtp_host="localhost",
        smtp_port=1025,
        smtp_username="",
        smtp_password="",
        smtp_from="NA Notes <notes@example.com>",
    )
    defaults.update(overrides)
    return Config(**defaults)


@pytest.fixture
def client(db: Database) -> TestClient:
    users_repo = UserRepository(db)
    notes_repo = NoteRepository(db)
    mailer = Mailer("localhost", 1025, "", "", "NA Notes <notes@example.com>")
    notes_service = NoteService(notes_repo, users_repo, mailer, "http://localhost:5173")
    state = AppState(
        config=build_config(),
        auth_store=AuthStore(db),
        oidc=FakeOidc(),
        users=users_repo,
        notes=notes_service,
    )
    app = create_app(state)
    return TestClient(app)


def login(client: TestClient, subject: str = "alice") -> tuple[str, str]:
    """Drives the full OIDC login flow and returns (session_cookie, csrf_token)."""
    resp = client.get("/api/auth/login", follow_redirects=False)
    assert resp.status_code == 302
    state = parse_qs(urlparse(resp.headers["location"]).query)["state"][0]

    resp = client.get(f"/api/auth/callback?code={subject}&state={state}", follow_redirects=False)
    assert resp.status_code == 302
    return resp.cookies[SESSION_COOKIE_NAME], resp.cookies[CSRF_COOKIE_NAME]


def test_healthz(client: TestClient) -> None:
    resp = client.get("/healthz")
    assert resp.status_code == 200


def test_login_sets_session_and_csrf_cookies(client: TestClient) -> None:
    session_cookie, csrf_token = login(client, "alice")
    assert session_cookie
    assert csrf_token

    resp = client.get("/api/auth/me")
    assert resp.status_code == 200
    assert resp.json()["email"] == "alice@example.com"


def test_me_requires_auth(client: TestClient) -> None:
    resp = client.get("/api/auth/me")
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "UNAUTHENTICATED"


def test_state_changing_request_without_csrf_header_is_rejected(client: TestClient) -> None:
    login(client)
    resp = client.post("/api/notes", json={"title": "T", "contentMarkdown": "c"})
    assert resp.status_code == 403
    assert resp.json()["error"]["code"] == "CSRF_REJECTED"


def test_note_crud_lifecycle(client: TestClient) -> None:
    _, csrf_token = login(client)
    headers = {CSRF_HEADER_NAME: csrf_token}

    resp = client.post("/api/notes", json={"title": "Hello", "contentMarkdown": "World"}, headers=headers)
    assert resp.status_code == 201
    note = resp.json()
    assert note["title"] == "Hello"
    assert note["version"] == 1
    assert note["myPermission"] == "owner"

    resp = client.get(f"/api/notes/{note['id']}")
    assert resp.status_code == 200

    resp = client.put(
        f"/api/notes/{note['id']}",
        json={"title": "Updated", "contentMarkdown": "New body"},
        headers={**headers, "If-Match": "1"},
    )
    assert resp.status_code == 200
    assert resp.json()["version"] == 2

    # Stale If-Match triggers a 409 carrying the current server copy.
    resp = client.put(
        f"/api/notes/{note['id']}",
        json={"title": "Stale", "contentMarkdown": "x"},
        headers={**headers, "If-Match": "1"},
    )
    assert resp.status_code == 409
    assert resp.json()["title"] == "Updated"

    resp = client.delete(f"/api/notes/{note['id']}", headers=headers)
    assert resp.status_code == 204

    resp = client.get(f"/api/notes/{note['id']}")
    assert resp.status_code == 404


def test_dashboard_shows_sample_note_when_logged_out(client: TestClient) -> None:
    resp = client.get("/api/notes")
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["items"]) == 1
    assert body["nextCursor"] is None


def test_public_note_view(client: TestClient) -> None:
    _, csrf_token = login(client)
    headers = {CSRF_HEADER_NAME: csrf_token}

    note = client.post(
        "/api/notes", json={"title": "Public", "contentMarkdown": "hi"}, headers=headers
    ).json()
    share = client.post(f"/api/notes/{note['id']}/public-share", headers=headers).json()

    resp = client.get(f"/api/public/notes/{share['token']}")
    assert resp.status_code == 200
    body = resp.json()
    assert body["title"] == "Public"
    assert "ownerId" not in body


def test_sharing_grants_access_to_target_user(client: TestClient) -> None:
    _, owner_csrf = login(client, "owner")
    headers = {CSRF_HEADER_NAME: owner_csrf}
    note = client.post("/api/notes", json={"title": "Shared", "contentMarkdown": "x"}, headers=headers).json()

    other_client = TestClient(client.app)
    _, other_csrf = login(other_client, "reader")
    other_id = other_client.get("/api/auth/me").json()["id"]

    client.post(
        f"/api/notes/{note['id']}/shares",
        json={"userId": other_id, "permission": "read"},
        headers=headers,
    )

    resp = other_client.get(f"/api/notes/{note['id']}")
    assert resp.status_code == 200
    assert resp.json()["myPermission"] == "read"
