package app.nanotes.backend.web

import app.nanotes.backend.apperr.ValidationException
import app.nanotes.backend.config.AppConfig
import app.nanotes.backend.notes.NoteService
import app.nanotes.backend.notes.Permission
import app.nanotes.backend.web.dto.CreateShareRequestDto
import app.nanotes.backend.web.dto.PublicShareDto
import app.nanotes.backend.web.dto.SharesDto
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/api/notes/{noteId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SharingResource(
    private val notes: NoteService,
    private val currentSession: CurrentSession,
    private val config: AppConfig,
) {
    @GET
    @Path("/shares")
    fun listShares(@PathParam("noteId") noteId: String): SharesDto {
        val userId = currentSession.requireUserId()
        val shares = notes.listShares(noteId, userId)
        val userShares = shares.userShares.map(Dtos::toUserShareDto)
        val publicShareDto: PublicShareDto? = shares.publicShare?.let { Dtos.toPublicShareDto(it, publicShareUrl(it.token)) }
        return SharesDto(userShares, publicShareDto)
    }

    @POST
    @Path("/shares")
    fun createShare(@PathParam("noteId") noteId: String, input: CreateShareRequestDto): Response {
        val userId = currentSession.requireUserId()
        if (input.permission != "read" && input.permission != "edit") {
            throw ValidationException("permission must be 'read' or 'edit'")
        }
        val share = notes.shareWithUser(noteId, userId, input.userId, Permission.fromWireValue(input.permission))
        return Response.status(Response.Status.CREATED).entity(Dtos.toUserShareDto(share)).build()
    }

    @DELETE
    @Path("/shares/{targetUserId}")
    fun deleteShare(@PathParam("noteId") noteId: String, @PathParam("targetUserId") targetUserId: String): Response {
        val userId = currentSession.requireUserId()
        notes.revokeShare(noteId, userId, targetUserId)
        return Response.noContent().build()
    }

    @POST
    @Path("/public-share")
    fun createPublicShare(@PathParam("noteId") noteId: String): Response {
        val userId = currentSession.requireUserId()
        val result = notes.createPublicShare(noteId, userId)
        return Response.status(Response.Status.CREATED).entity(Dtos.toPublicShareDto(result.share, result.url)).build()
    }

    @DELETE
    @Path("/public-share")
    fun deletePublicShare(@PathParam("noteId") noteId: String): Response {
        val userId = currentSession.requireUserId()
        notes.revokePublicShare(noteId, userId)
        return Response.noContent().build()
    }

    // Only used by listShares — createPublicShare gets its URL straight from
    // NoteService.PublicShareResult, which is built with the same "/shared/"
    // convention.
    private fun publicShareUrl(token: String): String = "${config.frontendUrl}/shared/$token"
}
