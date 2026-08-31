package app.nanotes.backend.web

import app.nanotes.backend.notes.NoteService
import app.nanotes.backend.web.dto.NoteDto
import app.nanotes.backend.web.dto.NoteInputDto
import app.nanotes.backend.web.dto.NotePageDto
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/api/notes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class NotesResource(private val notes: NoteService, private val currentSession: CurrentSession) {

    @GET
    fun list(@QueryParam("cursor") cursor: String?, @QueryParam("limit") limitParam: String?): NotePageDto {
        var limit = NoteService.INITIAL_PAGE_SIZE
        if (!limitParam.isNullOrEmpty()) {
            limit = QueryParams.parsePositiveInt(limitParam, "limit must be a positive integer")
        }
        limit = minOf(limit, MAX_PAGE_LIMIT)

        return Dtos.toNotePageDto(notes.list(currentSession.userId(), cursor, limit))
    }

    @POST
    fun create(input: NoteInputDto): Response {
        val userId = currentSession.requireUserId()
        val n = notes.create(userId, input.title, input.contentMarkdown, input.mentionedUserIds)
        return Response.status(Response.Status.CREATED).entity(Dtos.toNoteDto(n)).build()
    }

    @GET
    @Path("/{noteId}")
    fun get(@PathParam("noteId") noteId: String): NoteDto {
        val userId = currentSession.requireUserId()
        return Dtos.toNoteDto(notes.get(noteId, userId))
    }

    @PUT
    @Path("/{noteId}")
    fun update(
        @PathParam("noteId") noteId: String,
        @HeaderParam("If-Match") ifMatch: String?,
        input: NoteInputDto,
    ): NoteDto {
        val userId = currentSession.requireUserId()
        val expectedVersion = QueryParams.parseInt(ifMatch, "If-Match header must be the note's current version")
        val n = notes.update(noteId, userId, expectedVersion, input.title, input.contentMarkdown, input.mentionedUserIds)
        return Dtos.toNoteDto(n)
    }

    @DELETE
    @Path("/{noteId}")
    fun delete(@PathParam("noteId") noteId: String): Response {
        val userId = currentSession.requireUserId()
        notes.delete(noteId, userId)
        return Response.noContent().build()
    }

    companion object {
        private const val MAX_PAGE_LIMIT = 50
    }
}
