from __future__ import annotations

from fastapi import APIRouter, Depends

from app.api.deps import AppState, get_state
from app.api.dto import PublicNoteViewDTO, public_note_view_to_dto

router = APIRouter(tags=["public"])


@router.get("/public/notes/{token}")
def get_public_note(token: str, state: AppState = Depends(get_state)) -> PublicNoteViewDTO:
    view = state.notes.get_public_note(token)
    return public_note_view_to_dto(view)
