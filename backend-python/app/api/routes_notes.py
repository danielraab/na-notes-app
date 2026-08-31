from __future__ import annotations

from fastapi import APIRouter, Depends, Response
from starlette.requests import Request

from app.api.deps import AppState, get_state, get_viewer_id_optional, require_viewer_id
from app.api.dto import NoteDTO, NoteInputDTO, NotePageDTO, note_page_to_dto, note_to_dto
from app.api.errors import ApiError

router = APIRouter(tags=["notes"])

_MAX_PAGE_LIMIT = 50


@router.get("/notes")
def list_notes(
    cursor: str = "",
    limit: int = 12,
    state: AppState = Depends(get_state),
    viewer_id: str | None = Depends(get_viewer_id_optional),
) -> NotePageDTO:
    if limit <= 0:
        raise ApiError(400, "VALIDATION_ERROR", "limit must be a positive integer")
    limit = min(limit, _MAX_PAGE_LIMIT)

    page = state.notes.list_notes(viewer_id, cursor or None, limit)
    return note_page_to_dto(page)


@router.post("/notes", status_code=201)
def create_note(
    body: NoteInputDTO,
    state: AppState = Depends(get_state),
    user_id: str = Depends(require_viewer_id),
) -> NoteDTO:
    note = state.notes.create(user_id, body.title, body.content_markdown, body.mentioned_user_ids)
    return note_to_dto(note)


@router.get("/notes/{note_id}")
def get_note(
    note_id: str,
    state: AppState = Depends(get_state),
    user_id: str = Depends(require_viewer_id),
) -> NoteDTO:
    note = state.notes.get(note_id, user_id)
    return note_to_dto(note)


@router.put("/notes/{note_id}")
def update_note(
    note_id: str,
    body: NoteInputDTO,
    request: Request,
    state: AppState = Depends(get_state),
    user_id: str = Depends(require_viewer_id),
) -> NoteDTO:
    if_match = request.headers.get("If-Match", "")
    try:
        expected_version = int(if_match)
    except ValueError:
        raise ApiError(
            400, "VALIDATION_ERROR", "If-Match header must be the note's current version"
        ) from None

    note = state.notes.update(
        note_id, user_id, expected_version, body.title, body.content_markdown, body.mentioned_user_ids
    )
    return note_to_dto(note)


@router.delete("/notes/{note_id}", status_code=204)
def delete_note(
    note_id: str,
    state: AppState = Depends(get_state),
    user_id: str = Depends(require_viewer_id),
) -> Response:
    state.notes.delete(note_id, user_id)
    return Response(status_code=204)
