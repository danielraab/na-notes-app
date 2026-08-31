package app.nanotes.backend.web;

import app.nanotes.backend.apperr.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {
    @Override
    public Response toResponse(ForbiddenException e) {
        return ErrorResponses.of(Response.Status.FORBIDDEN, "FORBIDDEN", "not permitted");
    }
}
