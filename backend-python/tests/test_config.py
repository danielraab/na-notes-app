import pytest

from app.config import ConfigError, load

REQUIRED_VARS = [
    "SESSION_SECRET",
    "OIDC_ISSUER_URL",
    "OIDC_CLIENT_ID",
    "OIDC_CLIENT_SECRET",
    "OIDC_REDIRECT_URL",
]


def _set_all_required(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SESSION_SECRET", "s")
    monkeypatch.setenv("OIDC_ISSUER_URL", "https://issuer.example.com")
    monkeypatch.setenv("OIDC_CLIENT_ID", "client")
    monkeypatch.setenv("OIDC_CLIENT_SECRET", "secret")
    monkeypatch.setenv("OIDC_REDIRECT_URL", "http://localhost:8080/api/auth/callback")


def test_load_succeeds_with_required_vars(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_all_required(monkeypatch)
    cfg = load()
    assert cfg.listen_port == 8080
    assert cfg.allowed_origins == ["http://localhost:5173"]
    assert cfg.oidc_scopes == ["openid", "profile", "email"]


@pytest.mark.parametrize("missing", REQUIRED_VARS)
def test_load_fails_when_a_required_var_is_missing(monkeypatch: pytest.MonkeyPatch, missing: str) -> None:
    _set_all_required(monkeypatch)
    monkeypatch.delenv(missing, raising=False)
    with pytest.raises(ConfigError) as exc_info:
        load()
    assert missing in str(exc_info.value)


def test_listen_addr_parsed_into_host_and_port(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_all_required(monkeypatch)
    monkeypatch.setenv("LISTEN_ADDR", "0.0.0.0:9000")
    cfg = load()
    assert cfg.listen_host == "0.0.0.0"
    assert cfg.listen_port == 9000


def test_allowed_origins_split_and_trimmed(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_all_required(monkeypatch)
    monkeypatch.setenv("ALLOWED_ORIGINS", "http://a.example.com, http://b.example.com")
    cfg = load()
    assert cfg.allowed_origins == ["http://a.example.com", "http://b.example.com"]
