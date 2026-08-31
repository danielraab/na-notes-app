from __future__ import annotations

import hmac
import logging
import time

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from app.api.cookies import CSRF_HEADER_NAME, SESSION_COOKIE_NAME
from app.api.deps import AppState

logger = logging.getLogger("app.request")

_STATE_CHANGING_METHODS = {"POST", "PUT", "PATCH", "DELETE"}


class SessionContextMiddleware(BaseHTTPMiddleware):
    """Resolves the session cookie (if any) once per request and stores
    the result on request.state, so downstream dependencies/handlers never
    need to touch the session store themselves.
    """

    async def dispatch(self, request: Request, call_next):
        state: AppState = request.app.state.deps
        cookie = request.cookies.get(SESSION_COOKIE_NAME)
        if cookie:
            session = state.auth_store.get_session(cookie)
            if session is not None:
                request.state.user_id = session.user_id
                request.state.csrf_token = session.csrf_token
        return await call_next(request)


class CSRFMiddleware(BaseHTTPMiddleware):
    """Enforces the double-submit cookie pattern (ADR 0005) on
    state-changing requests. Requests without a session are let through
    unchecked here — the require_viewer_id dependency rejects them with
    401, the more useful error for a caller that was never going to be
    authorized anyway.
    """

    async def dispatch(self, request: Request, call_next):
        if request.method not in _STATE_CHANGING_METHODS:
            return await call_next(request)

        expected = getattr(request.state, "csrf_token", None)
        if expected is None:
            return await call_next(request)

        got = request.headers.get(CSRF_HEADER_NAME, "")
        if not got or not hmac.compare_digest(got, expected):
            error = {"code": "CSRF_REJECTED", "message": "missing or invalid CSRF token"}
            return JSONResponse(status_code=403, content={"error": error})
        return await call_next(request)


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        start = time.monotonic()
        response = await call_next(request)
        duration_ms = (time.monotonic() - start) * 1000
        logger.info(
            "%s %s -> %d (%.1fms)",
            request.method,
            request.url.path,
            response.status_code,
            duration_ms,
        )
        return response
