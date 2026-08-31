package app.nanotes.backend.apperr

import app.nanotes.backend.notes.Note

/** Resource does not exist, or is not visible to the caller. */
class NotFoundException(message: String = "not found") : RuntimeException(message)

/** Logged in, but not permitted to perform this action. */
class ForbiddenException : RuntimeException("forbidden")

/** Request failed input validation. */
class ValidationException(message: String) : RuntimeException(message)

/** Not logged in. */
class UnauthorizedException : RuntimeException("login required")

/**
 * The note's stored version no longer matches the client's If-Match header
 * (ADR 0008). Carries the current server copy so the 409 response body can
 * include it, mirroring backend-go's Update returning (Note, error) together.
 */
class VersionConflictException(val currentNote: Note) : RuntimeException("note was modified since you last loaded it")
