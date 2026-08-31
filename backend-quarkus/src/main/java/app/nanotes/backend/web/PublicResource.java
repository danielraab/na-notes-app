package app.nanotes.backend.web;

import app.nanotes.backend.notes.NoteService;
import app.nanotes.backend.web.dto.PublicNoteViewDto;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/public/notes/{token}")
@Produces(MediaType.APPLICATION_JSON)
public class PublicResource {

    private final NoteService notes;

    public PublicResource(NoteService notes) {
        this.notes = notes;
    }

    @GET
    public PublicNoteViewDto get(@PathParam("token") String token) {
        return Dtos.toPublicNoteViewDto(notes.getPublicNote(token));
    }
}
