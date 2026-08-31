package app.nanotes.backend.web;

import app.nanotes.backend.apperr.ValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {
    @Override
    public Response toResponse(ValidationException e) {
        return ErrorResponses.of(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }
}
