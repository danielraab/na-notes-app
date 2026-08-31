package app.nanotes.backend.web;

import app.nanotes.backend.notes.Note;
import app.nanotes.backend.notes.NoteService;
import app.nanotes.backend.notes.Page;
import app.nanotes.backend.web.dto.NoteDto;
import app.nanotes.backend.web.dto.NoteInputDto;
import app.nanotes.backend.web.dto.NotePageDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/notes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotesResource {

    private static final int MAX_PAGE_LIMIT = 50;

    private final NoteService notes;
    private final CurrentSession currentSession;

    public NotesResource(NoteService notes, CurrentSession currentSession) {
        this.notes = notes;
        this.currentSession = currentSession;
    }

    @GET
    public NotePageDto list(@QueryParam("cursor") String cursor, @QueryParam("limit") String limitParam) {
        int limit = NoteService.INITIAL_PAGE_SIZE;
        if (limitParam != null && !limitParam.isEmpty()) {
            limit = QueryParams.parsePositiveInt(limitParam, "limit must be a positive integer");
        }
        limit = Math.min(limit, MAX_PAGE_LIMIT);

        Page page = notes.list(currentSession.userId(), cursor, limit);
        return Dtos.toNotePageDto(page);
    }

    @POST
    public Response create(NoteInputDto in) {
        String userId = currentSession.requireUserId();
        Note n = notes.create(userId, in.title(), in.contentMarkdown(), in.mentionedUserIds());
        return Response.status(Response.Status.CREATED).entity(Dtos.toNoteDto(n)).build();
    }

    @GET
    @Path("/{noteId}")
    public NoteDto get(@PathParam("noteId") String noteId) {
        String userId = currentSession.requireUserId();
        return Dtos.toNoteDto(notes.get(noteId, userId));
    }

    @PUT
    @Path("/{noteId}")
    public NoteDto update(@PathParam("noteId") String noteId, @HeaderParam("If-Match") String ifMatch, NoteInputDto in) {
        String userId = currentSession.requireUserId();
        int expectedVersion = QueryParams.parseInt(ifMatch, "If-Match header must be the note's current version");
        Note n = notes.update(noteId, userId, expectedVersion, in.title(), in.contentMarkdown(), in.mentionedUserIds());
        return Dtos.toNoteDto(n);
    }

    @DELETE
    @Path("/{noteId}")
    public Response delete(@PathParam("noteId") String noteId) {
        String userId = currentSession.requireUserId();
        notes.delete(noteId, userId);
        return Response.noContent().build();
    }
}
