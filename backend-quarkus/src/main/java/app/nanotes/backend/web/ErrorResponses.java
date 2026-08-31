package app.nanotes.backend.web;

import app.nanotes.backend.web.dto.ErrorDto;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public final class ErrorResponses {

    private ErrorResponses() {}

    public static Response of(Response.Status status, String code, String message) {
        return Response.status(status).entity(ErrorDto.of(code, message)).type(MediaType.APPLICATION_JSON).build();
    }
}
