package app.nanotes.backend.web;

import app.nanotes.backend.apperr.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
    @Override
    public Response toResponse(NotFoundException e) {
        return ErrorResponses.of(Response.Status.NOT_FOUND, "NOT_FOUND", "resource not found");
    }
}
