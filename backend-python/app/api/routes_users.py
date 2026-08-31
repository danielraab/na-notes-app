from __future__ import annotations

from fastapi import APIRouter, Depends

from app.api.deps import AppState, get_state, require_viewer_id
from app.api.dto import UserSummaryDTO, user_summary_to_dto
from app.api.errors import ApiError

router = APIRouter(tags=["users"])

_MAX_USER_SEARCH_LIMIT = 25


@router.get("/users/search")
def search_users(
    q: str = "",
    limit: int = 10,
    state: AppState = Depends(get_state),
    user_id: str = Depends(require_viewer_id),
) -> list[UserSummaryDTO]:
    if not q:
        raise ApiError(400, "VALIDATION_ERROR", "q is required")
    if limit <= 0:
        raise ApiError(400, "VALIDATION_ERROR", "limit must be a positive integer")
    limit = min(limit, _MAX_USER_SEARCH_LIMIT)

    results = state.users.search(user_id, q, limit)
    return [user_summary_to_dto(u) for u in results]
