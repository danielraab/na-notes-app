"""Process entrypoint: loads configuration, opens the database, discovers
the OIDC provider, wires the domain services, and builds the ASGI app.
Run with `uvicorn app.main:app` (see Dockerfile / README).
"""

from __future__ import annotations

import logging
import sys

from app import config as config_module
from app import db as db_module
from app.api.deps import AppState
from app.app import create_app
from app.auth.oidc import OidcClient, OidcError
from app.auth.store import AuthStore
from app.mail import Mailer
from app.notes.repository import NoteRepository
from app.notes.service import NoteService
from app.users.repository import UserRepository

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("app.main")


def build_state() -> AppState:
    cfg = config_module.load()

    database = db_module.open_database(cfg.database_url)

    oidc = OidcClient.discover(
        cfg.oidc_issuer_url,
        cfg.oidc_client_id,
        cfg.oidc_client_secret,
        cfg.oidc_redirect_url,
        cfg.oidc_scopes,
    )

    users_repo = UserRepository(database)
    notes_repo = NoteRepository(database)
    mailer = Mailer(cfg.smtp_host, cfg.smtp_port, cfg.smtp_username, cfg.smtp_password, cfg.smtp_from)
    notes_service = NoteService(notes_repo, users_repo, mailer, cfg.frontend_url)
    auth_store = AuthStore(database)

    return AppState(config=cfg, auth_store=auth_store, oidc=oidc, users=users_repo, notes=notes_service)


def create_production_app():
    try:
        state = build_state()
    except (config_module.ConfigError, OidcError, db_module.UnsupportedDatabaseError) as exc:
        logger.error("server failed to start: %s", exc)
        sys.exit(1)
    return create_app(state)


app = create_production_app()


if __name__ == "__main__":
    import uvicorn

    state: AppState = app.state.deps
    uvicorn.run(app, host=state.config.listen_host, port=state.config.listen_port)
