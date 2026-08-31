package app.nanotes.backend.web

import app.nanotes.backend.notes.NoteService
import app.nanotes.backend.web.dto.PublicNoteViewDto
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/api/public/notes/{token}")
@Produces(MediaType.APPLICATION_JSON)
class PublicResource(private val notes: NoteService) {
    @GET
    fun get(@PathParam("token") token: String): PublicNoteViewDto = Dtos.toPublicNoteViewDto(notes.getPublicNote(token))
}
