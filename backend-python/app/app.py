"""Builds the ASGI application from an already-wired AppState: routing,
CORS/CSRF/session middleware, and exception mapping. Domain logic itself
lives in app.notes/app.users/app.auth — this module (plus app/api/*) is
the only place that knows about HTTP.

Kept separate from app/main.py so tests can build an app around a fake/
in-memory AppState without going through real OIDC discovery or process
environment variables.
"""

from __future__ import annotations

from fastapi import FastAPI
from starlette.middleware.cors import CORSMiddleware
from starlette.responses import Response

from app.api import routes_auth, routes_notes, routes_public, routes_sharing, routes_users
from app.api.cookies import CSRF_HEADER_NAME
from app.api.deps import AppState
from app.api.errors import register_exception_handlers
from app.api.middleware import CSRFMiddleware, RequestLoggingMiddleware, SessionContextMiddleware


def create_app(state: AppState) -> FastAPI:
    app = FastAPI(
        title="NA Notes API (Python)",
        # /openapi/openapi.yaml at the repo root is the source of truth
        # (ADR 0003) — don't publish a second, independently-generated
        # spec that could drift from it.
        openapi_url=None,
        docs_url=None,
        redoc_url=None,
    )
    app.state.deps = state

    register_exception_handlers(app)

    @app.get("/healthz", include_in_schema=False)
    def healthz() -> Response:
        return Response(status_code=200)

    app.include_router(routes_auth.router, prefix="/api")
    app.include_router(routes_users.router, prefix="/api")
    app.include_router(routes_notes.router, prefix="/api")
    app.include_router(routes_sharing.router, prefix="/api")
    app.include_router(routes_public.router, prefix="/api")

    # Starlette's add_middleware prepends to its middleware list, so the
    # *last* call here ends up outermost. To get the same outer-to-inner
    # order backend-go's NewRouter uses (request logging -> CORS -> session
    # lookup -> CSRF -> routes), register them in the reverse of that order.
    app.add_middleware(CSRFMiddleware)
    app.add_middleware(SessionContextMiddleware)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=state.config.allowed_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
        allow_headers=["Content-Type", "If-Match", CSRF_HEADER_NAME],
    )
    app.add_middleware(RequestLoggingMiddleware)

    return app
