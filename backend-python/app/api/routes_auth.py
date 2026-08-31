from __future__ import annotations

import logging

from fastapi import APIRouter, Depends, Request
from starlette.responses import RedirectResponse, Response

from app.api.cookies import CSRF_COOKIE_NAME, SESSION_COOKIE_NAME
from app.api.deps import AppState, get_state, require_viewer_id
from app.api.dto import user_to_dto
from app.api.errors import ApiError
from app.apperr import NotFoundError
from app.auth.oidc import OidcError
from app.timeutil import now_utc

logger = logging.getLogger("app.auth")

router = APIRouter(tags=["auth"])


def _is_safe_redirect_path(path: str) -> bool:
    """Restricts post-login redirects to an in-app path, to avoid the
    login flow being used as an open redirect."""
    return bool(path) and path.startswith("/") and not path.startswith("//")


def _set_auth_cookies(
    response: Response, state: AppState, session_id: str, csrf_token: str, max_age: int
) -> None:
    secure = state.config.public_base_url.startswith("https://")
    response.set_cookie(
        key=SESSION_COOKIE_NAME,
        value=session_id,
        path="/",
        domain=state.config.cookie_domain or None,
        httponly=True,
        secure=secure,
        samesite="lax",
        max_age=max_age,
    )
    # Readable by frontend JS on purpose — it's echoed back as the
    # X-CSRF-Token header, never trusted as an identity credential itself.
    response.set_cookie(
        key=CSRF_COOKIE_NAME,
        value=csrf_token,
        path="/",
        domain=state.config.cookie_domain or None,
        httponly=False,
        secure=secure,
        samesite="lax",
        max_age=max_age,
    )


def _clear_auth_cookies(response: Response, state: AppState) -> None:
    for name in (SESSION_COOKIE_NAME, CSRF_COOKIE_NAME):
        response.delete_cookie(key=name, path="/", domain=state.config.cookie_domain or None)


@router.get("/auth/login")
def start_login(redirectTo: str = "", state: AppState = Depends(get_state)) -> RedirectResponse:
    redirect_to = redirectTo if _is_safe_redirect_path(redirectTo) else "/"
    request = state.auth_store.create_oidc_request(redirect_to)
    url = state.oidc.auth_code_url(request.state, request.code_verifier)
    return RedirectResponse(url=url, status_code=302)


@router.get("/auth/callback")
def handle_login_callback(
    code: str = "", state: str = "", app_state: AppState = Depends(get_state)
) -> RedirectResponse:
    if not code or not state:
        raise ApiError(400, "VALIDATION_ERROR", "missing code or state")

    oidc_request = app_state.auth_store.consume_oidc_request(state)
    if oidc_request is None:
        raise ApiError(400, "INVALID_STATE", "login request expired or was already used")

    try:
        claims = app_state.oidc.exchange(code, oidc_request.code_verifier)
    except OidcError:
        logger.exception("oidc exchange failed")
        raise ApiError(
            502, "OIDC_EXCHANGE_FAILED", "could not complete login with identity provider"
        ) from None

    user = app_state.users.upsert_from_oidc(
        claims.subject, claims.email, claims.display_name, claims.avatar_url
    )
    session = app_state.auth_store.create_session(user.id)

    max_age = int((session.expires_at - now_utc()).total_seconds())
    redirect = RedirectResponse(url=app_state.config.frontend_url + oidc_request.redirect_to, status_code=302)
    _set_auth_cookies(redirect, app_state, session.id, session.csrf_token, max_age)
    return redirect


@router.post("/auth/logout", status_code=204)
def logout(
    request: Request,
    state: AppState = Depends(get_state),
    _user_id: str = Depends(require_viewer_id),
) -> Response:
    cookie = request.cookies.get(SESSION_COOKIE_NAME)
    if cookie:
        state.auth_store.delete_session(cookie)
    response = Response(status_code=204)
    _clear_auth_cookies(response, state)
    return response


@router.get("/auth/me")
def get_current_user(request: Request, state: AppState = Depends(get_state)):
    user_id = getattr(request.state, "user_id", None)
    if not user_id:
        raise ApiError(401, "UNAUTHENTICATED", "login required")
    user = state.users.get_by_id(user_id)
    if user is None:
        raise NotFoundError("current user not found")
    return user_to_dto(user)
