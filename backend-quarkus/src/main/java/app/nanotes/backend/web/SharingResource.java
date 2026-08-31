package app.nanotes.backend.web;

import app.nanotes.backend.apperr.ValidationException;
import app.nanotes.backend.config.AppConfig;
import app.nanotes.backend.notes.NoteService;
import app.nanotes.backend.notes.Permission;
import app.nanotes.backend.notes.UserShare;
import app.nanotes.backend.web.dto.CreateShareRequestDto;
import app.nanotes.backend.web.dto.PublicShareDto;
import app.nanotes.backend.web.dto.SharesDto;
import app.nanotes.backend.web.dto.UserShareDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/notes/{noteId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SharingResource {

    private final NoteService notes;
    private final CurrentSession currentSession;
    private final AppConfig config;

    public SharingResource(NoteService notes, CurrentSession currentSession, AppConfig config) {
        this.notes = notes;
        this.currentSession = currentSession;
        this.config = config;
    }

    @GET
    @Path("/shares")
    public SharesDto listShares(@PathParam("noteId") String noteId) {
        String userId = currentSession.requireUserId();
        NoteService.Shares shares = notes.listShares(noteId, userId);
        var userShares = shares.userShares().stream().map(Dtos::toUserShareDto).toList();
        PublicShareDto publicShareDto = shares.publicShare() == null
                ? null
                : Dtos.toPublicShareDto(shares.publicShare(), publicShareUrl(shares.publicShare().token()));
        return new SharesDto(userShares, publicShareDto);
    }

    @POST
    @Path("/shares")
    public Response createShare(@PathParam("noteId") String noteId, CreateShareRequestDto in) {
        String userId = currentSession.requireUserId();
        if (!"read".equals(in.permission()) && !"edit".equals(in.permission())) {
            throw new ValidationException("permission must be 'read' or 'edit'");
        }
        UserShare share = notes.shareWithUser(noteId, userId, in.userId(), Permission.fromWireValue(in.permission()));
        return Response.status(Response.Status.CREATED).entity(Dtos.toUserShareDto(share)).build();
    }

    @DELETE
    @Path("/shares/{targetUserId}")
    public Response deleteShare(@PathParam("noteId") String noteId, @PathParam("targetUserId") String targetUserId) {
        String userId = currentSession.requireUserId();
        notes.revokeShare(noteId, userId, targetUserId);
        return Response.noContent().build();
    }

    @POST
    @Path("/public-share")
    public Response createPublicShare(@PathParam("noteId") String noteId) {
        String userId = currentSession.requireUserId();
        NoteService.PublicShareResult result = notes.createPublicShare(noteId, userId);
        return Response.status(Response.Status.CREATED).entity(Dtos.toPublicShareDto(result.share(), result.url())).build();
    }

    @DELETE
    @Path("/public-share")
    public Response deletePublicShare(@PathParam("noteId") String noteId) {
        String userId = currentSession.requireUserId();
        notes.revokePublicShare(noteId, userId);
        return Response.noContent().build();
    }

    // Only used by listShares — createPublicShare gets its URL straight from
    // NoteService.PublicShareResult, which is built with the same "/shared/"
    // convention.
    private String publicShareUrl(String token) {
        return config.frontendUrl() + "/shared/" + token;
    }
}
