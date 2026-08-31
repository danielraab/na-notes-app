package app.nanotes.backend.web;

import app.nanotes.backend.apperr.VersionConflictException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** 409, with the current server copy of the note as the body (ADR 0008). */
@Provider
public class VersionConflictExceptionMapper implements ExceptionMapper<VersionConflictException> {
    @Override
    public Response toResponse(VersionConflictException e) {
        return Response.status(Response.Status.CONFLICT).entity(Dtos.toNoteDto(e.currentNote())).build();
    }
}
