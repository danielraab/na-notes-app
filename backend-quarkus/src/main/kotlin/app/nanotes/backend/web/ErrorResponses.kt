package app.nanotes.backend.web

import app.nanotes.backend.web.dto.ErrorDto
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

object ErrorResponses {
    fun of(status: Response.Status, code: String, message: String): Response =
        Response.status(status).entity(ErrorDto.of(code, message)).type(MediaType.APPLICATION_JSON).build()
}
