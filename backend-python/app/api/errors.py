"""Maps domain/validation errors onto the {"error": {"code", "message"}}
shape every backend implementation returns (openapi Error schema).
Route handlers raise ApiError directly for request-shape problems (bad
query params, malformed body); domain packages raise the sentinel errors
in app.apperr, which are mapped here too, so handlers never need to know
about HTTP status codes.
"""

from __future__ import annotations

import logging

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.api.dto import note_to_dto
from app.apperr import ForbiddenError, NotFoundError, ValidationError, VersionConflictError

logger = logging.getLogger("app.api")


class ApiError(Exception):
    def __init__(self, status_code: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message


def _error_body(code: str, message: str) -> dict:
    return {"error": {"code": code, "message": message}}


def register_exception_handlers(app) -> None:
    @app.exception_handler(ApiError)
    async def handle_api_error(request: Request, exc: ApiError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=_error_body(exc.code, exc.message))

    @app.exception_handler(NotFoundError)
    async def handle_not_found(request: Request, exc: NotFoundError) -> JSONResponse:
        return JSONResponse(status_code=404, content=_error_body("NOT_FOUND", "resource not found"))

    @app.exception_handler(ForbiddenError)
    async def handle_forbidden(request: Request, exc: ForbiddenError) -> JSONResponse:
        return JSONResponse(status_code=403, content=_error_body("FORBIDDEN", "not permitted"))

    @app.exception_handler(ValidationError)
    async def handle_validation(request: Request, exc: ValidationError) -> JSONResponse:
        return JSONResponse(status_code=400, content=_error_body("VALIDATION_ERROR", str(exc)))

    @app.exception_handler(VersionConflictError)
    async def handle_version_conflict(request: Request, exc: VersionConflictError) -> JSONResponse:
        body = note_to_dto(exc.current).model_dump(by_alias=True)
        return JSONResponse(status_code=409, content=body)

    @app.exception_handler(RequestValidationError)
    async def handle_request_validation(request: Request, exc: RequestValidationError) -> JSONResponse:
        return JSONResponse(status_code=400, content=_error_body("VALIDATION_ERROR", "invalid request body"))

    @app.exception_handler(StarletteHTTPException)
    async def handle_http_exception(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        # Starlette raises this itself for e.g. 404 route-not-found and 405
        # method-not-allowed, before any handler runs.
        code = "NOT_FOUND" if exc.status_code == 404 else "ERROR"
        detail = exc.detail if isinstance(exc.detail, str) else code
        return JSONResponse(status_code=exc.status_code, content=_error_body(code, detail))

    @app.exception_handler(Exception)
    async def handle_unexpected(request: Request, exc: Exception) -> JSONResponse:
        logger.exception("unhandled error")
        return JSONResponse(status_code=500, content=_error_body("INTERNAL", "internal server error"))
