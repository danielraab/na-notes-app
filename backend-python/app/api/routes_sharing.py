from __future__ import annotations

from fastapi import APIRouter, Depends, Response

from app.api.deps import AppState, get_state, require_viewer_id
from app.api.dto import (
    ListSharesDTO,
    PublicShareDTO,
    ShareCreateDTO,
    UserShareDTO,
    public_share_to_dto,
    user_share_to_dto,
)
from app.api.errors import ApiError
from app.notes.models import Permission

router = APIRouter(tags=["sharing"])


@router.get("/notes/{note_id}/shares")
def list_note_shares(
    note_id: str,
    state: AppState = Depends(get_state),
    user_id: str = Depends(require_viewer_id),
) -> ListSharesDTO:
    shares, public = state.notes.list_shares(note_id, user_id)
    public_dto = None
    if public is not None:
        public_dto = public_share_to_dto(public, f"{state.config.frontend_url}/shared/{public.token}")
    return ListSharesDTO(user_shares=[user_share_to_dto(s) for s in shares], public_share=public_dto)


@router.post("/notes/{note_id}/shares", status_code=201)
def create_note_share(
    note_id: str,
    body: ShareCreateDTO,
    state: AppState = Depends(get_state),
    user_id: str = Depends(require_viewer_id),
) -> UserShareDTO:
    if body.permission not in (Permission.READ.value, Permission.EDIT.value):
        raise ApiError(400, "VALIDATION_ERROR", "permission must be 'read' or 'edit'")

    share = state.notes.share_with_user(note_id, user_id, body.user_id, Permission(body.permission))
    return user_share_to_dto(share)


@router.delete("/notes/{note_id}/shares/{target_user_id}", status_code=204)
def delete_note_share(
    note_id: str,
    target_user_id: str,
    state: AppState = Depends(get_state),
    user_id: str = Depends(require_viewer_id),
) -> Response:
    state.notes.revoke_share(note_id, user_id, target_user_id)
    return Response(status_code=204)


@router.post("/notes/{note_id}/public-share", status_code=201)
def create_public_share(
    note_id: str,
    state: AppState = Depends(get_state),
    user_id: str = Depends(require_viewer_id),
) -> PublicShareDTO:
    share, url = state.notes.create_public_share(note_id, user_id)
    return public_share_to_dto(share, url)


@router.delete("/notes/{note_id}/public-share", status_code=204)
def delete_public_share(
    note_id: str,
    state: AppState = Depends(get_state),
    user_id: str = Depends(require_viewer_id),
) -> Response:
    state.notes.revoke_public_share(note_id, user_id)
    return Response(status_code=204)
