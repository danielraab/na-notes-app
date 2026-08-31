"""Runtime configuration loaded from environment variables.

Every value here is shared, by name, with every other backend
implementation in this repository (see /README.md and
/docs/adr/0011-per-implementation-env-files.md) — do not rename without
updating this folder's .env.example and the root docs.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field


def _get_env(key: str, fallback: str = "") -> str:
    return os.environ.get(key) or fallback


def _split_csv(value: str) -> list[str]:
    return [p.strip() for p in value.split(",") if p.strip()]


@dataclass
class Config:
    # HTTP
    listen_host: str
    listen_port: int
    public_base_url: str
    frontend_url: str
    allowed_origins: list[str]

    # Session / CSRF
    session_secret: str
    cookie_domain: str

    # OIDC
    oidc_issuer_url: str
    oidc_client_id: str
    oidc_client_secret: str
    oidc_redirect_url: str
    oidc_scopes: list[str]

    # Database. The URL's scheme selects the engine (ADR 0013). This
    # implementation only supports SQLite — see
    # docs/decisions/0002-sqlite-only.md for why PostgreSQL support (opt-in
    # per ADR 0013) was not added here.
    database_url: str

    # SMTP
    smtp_host: str
    smtp_port: int
    smtp_username: str
    smtp_password: str
    smtp_from: str

    missing: list[str] = field(default_factory=list)


class ConfigError(RuntimeError):
    pass


def load() -> Config:
    listen_addr = _get_env("LISTEN_ADDR", ":8080")
    host, _, port = listen_addr.rpartition(":")
    cfg = Config(
        listen_host=host or "0.0.0.0",
        listen_port=int(port) if port else 8080,
        public_base_url=_get_env("PUBLIC_BASE_URL", "http://localhost:8080"),
        frontend_url=_get_env("FRONTEND_URL", "http://localhost:5173"),
        allowed_origins=_split_csv(_get_env("ALLOWED_ORIGINS", "http://localhost:5173")),
        session_secret=_get_env("SESSION_SECRET"),
        cookie_domain=_get_env("COOKIE_DOMAIN"),
        oidc_issuer_url=_get_env("OIDC_ISSUER_URL"),
        oidc_client_id=_get_env("OIDC_CLIENT_ID"),
        oidc_client_secret=_get_env("OIDC_CLIENT_SECRET"),
        oidc_redirect_url=_get_env("OIDC_REDIRECT_URL"),
        oidc_scopes=_split_csv(_get_env("OIDC_SCOPES", "openid,profile,email")),
        database_url=_get_env("DATABASE_URL", "./notes.db"),
        smtp_host=_get_env("SMTP_HOST", "localhost"),
        smtp_port=int(_get_env("SMTP_PORT", "25")),
        smtp_username=_get_env("SMTP_USERNAME"),
        smtp_password=_get_env("SMTP_PASSWORD"),
        smtp_from=_get_env("SMTP_FROM", "NA Notes <notes@example.com>"),
    )

    missing = []
    if not cfg.session_secret:
        missing.append("SESSION_SECRET")
    if not cfg.oidc_issuer_url:
        missing.append("OIDC_ISSUER_URL")
    if not cfg.oidc_client_id:
        missing.append("OIDC_CLIENT_ID")
    if not cfg.oidc_client_secret:
        missing.append("OIDC_CLIENT_SECRET")
    if not cfg.oidc_redirect_url:
        missing.append("OIDC_REDIRECT_URL")
    if missing:
        raise ConfigError(f"missing required environment variables: {', '.join(missing)}")

    return cfg
