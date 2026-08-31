// Maps the small set of sentinel exceptions domain classes throw onto HTTP
// status codes, mirroring backend-go's respondDomainError. Anything not
// covered by a specific mapper here (see UnhandledExceptionMapper) is an
// unexpected server error.
package app.nanotes.backend.web

import app.nanotes.backend.apperr.ForbiddenException
import app.nanotes.backend.apperr.NotFoundException
import app.nanotes.backend.apperr.UnauthorizedException
import app.nanotes.backend.apperr.ValidationException
import app.nanotes.backend.apperr.VersionConflictException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger

@Provider
class NotFoundExceptionMapper : ExceptionMapper<NotFoundException> {
    override fun toResponse(e: NotFoundException): Response =
        ErrorResponses.of(Response.Status.NOT_FOUND, "NOT_FOUND", "resource not found")
}

@Provider
class ForbiddenExceptionMapper : ExceptionMapper<ForbiddenException> {
    override fun toResponse(e: ForbiddenException): Response =
        ErrorResponses.of(Response.Status.FORBIDDEN, "FORBIDDEN", "not permitted")
}

@Provider
class UnauthorizedExceptionMapper : ExceptionMapper<UnauthorizedException> {
    override fun toResponse(e: UnauthorizedException): Response =
        ErrorResponses.of(Response.Status.UNAUTHORIZED, "UNAUTHENTICATED", "login required")
}

@Provider
class ValidationExceptionMapper : ExceptionMapper<ValidationException> {
    override fun toResponse(e: ValidationException): Response =
        ErrorResponses.of(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", e.message ?: "invalid request")
}

/** 409, with the current server copy of the note as the body (ADR 0008). */
@Provider
class VersionConflictExceptionMapper : ExceptionMapper<VersionConflictException> {
    override fun toResponse(e: VersionConflictException): Response =
        Response.status(Response.Status.CONFLICT).entity(Dtos.toNoteDto(e.currentNote)).build()
}

/** Anything not covered by a more specific mapper is an unexpected server error, logged with detail the client never sees. */
@Provider
class UnhandledExceptionMapper : ExceptionMapper<Throwable> {
    companion object {
        private val LOG: Logger = Logger.getLogger(UnhandledExceptionMapper::class.java)
    }

    override fun toResponse(e: Throwable): Response {
        if (e is WebApplicationException) {
            return e.response
        }
        LOG.error("unhandled error", e)
        return ErrorResponses.of(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL", "internal server error")
    }
}
