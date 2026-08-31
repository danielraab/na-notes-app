package app.nanotes.backend.web;

import app.nanotes.backend.apperr.UnauthorizedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {
    @Override
    public Response toResponse(UnauthorizedException e) {
        return ErrorResponses.of(Response.Status.UNAUTHORIZED, "UNAUTHENTICATED", "login required");
    }
}
