package app.nanotes.backend.web

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response

@Path("/healthz")
class HealthResource {
    @GET
    fun health(): Response = Response.ok().build()
}
