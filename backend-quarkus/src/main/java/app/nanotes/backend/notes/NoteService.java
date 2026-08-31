package app.nanotes.backend.notes;

import app.nanotes.backend.apperr.ForbiddenException;
import app.nanotes.backend.apperr.NotFoundException;
import app.nanotes.backend.apperr.ValidationException;
import app.nanotes.backend.apperr.VersionConflictException;
import app.nanotes.backend.config.AppConfig;
import app.nanotes.backend.mail.Mailer;
import app.nanotes.backend.users.User;
import app.nanotes.backend.users.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class NoteService {

    public static final int INITIAL_PAGE_SIZE = 12;

    private static final String SAMPLE_NOTE_ID = "00000000-0000-0000-0000-000000000000";

    private final NoteRepository repo;
    private final UserRepository users;
    private final Mailer mailer;
    /** Frontend origin, for links in emails and public share URLs. */
    private final String baseUrl;

    public NoteService(NoteRepository repo, UserRepository users, Mailer mailer, AppConfig config) {
        this.repo = repo;
        this.users = users;
        this.mailer = mailer;
        this.baseUrl = config.frontendUrl();
    }

    private static NoteSummary sampleNote() {
        return new NoteSummary(
                SAMPLE_NOTE_ID,
                "Welcome to NA Notes",
                "Sign in to create your own notes, share them with teammates, and mention people to loop them in.",
                SAMPLE_NOTE_ID,
                Permission.READ,
                true,
                Instant.now());
    }

    /** The dashboard feed. An anonymous viewer (viewerId null/blank) always sees exactly the sample note. */
    public Page list(String viewerId, String cursor, int limit) {
        if (viewerId == null || viewerId.isEmpty()) {
            return new Page(List.of(sampleNote()), null);
        }
        return repo.listForViewer(viewerId, cursor, limit <= 0 ? INITIAL_PAGE_SIZE : limit);
    }

    /**
     * Fetches a note for viewerId, resolving their effective permission. A
     * viewer with no ownership or share record gets NotFoundException rather
     * than ForbiddenException, so the endpoint doesn't reveal the note exists.
     */
    public Note get(String noteId, String viewerId) {
        Note n = repo.getById(noteId);
        if (n.ownerId().equals(viewerId)) {
            return n.withMyPermission(Permission.OWNER);
        }
        Optional<Permission> perm = repo.sharePermission(noteId, viewerId);
        if (perm.isEmpty()) {
            throw new NotFoundException();
        }
        return n.withMyPermission(perm.get());
    }

    public Note create(String ownerId, String title, String content, List<String> mentionedUserIds) {
        if (title == null || title.isEmpty()) {
            throw new ValidationException("title is required");
        }
        Note n = repo.create(ownerId, title, content);
        notifyMentions(n, ownerId, Set.of(), mentionedUserIds);
        return n;
    }

    public Note update(String noteId, String actorId, int expectedVersion, String title, String content, List<String> mentionedUserIds) {
        if (title == null || title.isEmpty()) {
            throw new ValidationException("title is required");
        }
        Note current = get(noteId, actorId);
        if (current.myPermission() != Permission.OWNER && current.myPermission() != Permission.EDIT) {
            throw new ForbiddenException();
        }

        Set<String> existingMentions = repo.existingMentions(noteId);

        Note updated;
        try {
            updated = repo.update(noteId, title, content, expectedVersion).withMyPermission(current.myPermission());
        } catch (VersionConflictException e) {
            throw new VersionConflictException(e.currentNote().withMyPermission(current.myPermission()));
        }

        notifyMentions(updated, actorId, existingMentions, mentionedUserIds);
        return updated;
    }

    /**
     * Records mentionedUserIds against the note and emails only the ones
     * not already present in alreadyMentioned, so editing a note doesn't
     * re-notify people mentioned in an earlier version.
     */
    private void notifyMentions(Note n, String actorId, Set<String> alreadyMentioned, List<String> mentionedUserIds) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return;
        }
        repo.addMentions(n.id(), mentionedUserIds);
        User actor = users.getById(actorId);
        String noteUrl = baseUrl + "/notes/" + n.id();
        for (String uid : mentionedUserIds) {
            if (alreadyMentioned.contains(uid) || uid.equals(actorId)) {
                continue;
            }
            User mentioned;
            try {
                mentioned = users.getById(uid);
            } catch (NotFoundException e) {
                continue; // unknown/invalid mention target: skip rather than fail the save
            }
            mailer.notifyMentioned(mentioned.email(), actor.displayName(), n.title(), noteUrl);
        }
    }

    public void delete(String noteId, String actorId) {
        Note n = repo.getById(noteId);
        if (!n.ownerId().equals(actorId)) {
            throw new ForbiddenException();
        }
        repo.delete(noteId);
    }

    private Note requireOwner(String noteId, String actorId) {
        Note n = repo.getById(noteId);
        if (!n.ownerId().equals(actorId)) {
            throw new ForbiddenException();
        }
        return n;
    }

    public record Shares(List<UserShare> userShares, PublicShare publicShare) {}

    public Shares listShares(String noteId, String actorId) {
        requireOwner(noteId, actorId);
        List<UserShare> shares = repo.listShares(noteId);
        PublicShare publicShare = repo.getPublicShare(noteId).orElse(null);
        return new Shares(shares, publicShare);
    }

    public UserShare shareWithUser(String noteId, String actorId, String targetUserId, Permission permission) {
        Note n = requireOwner(noteId, actorId);
        if (targetUserId.equals(actorId)) {
            throw new ValidationException("cannot share a note with yourself");
        }
        User target;
        try {
            target = users.getById(targetUserId);
        } catch (NotFoundException e) {
            throw new ValidationException("unknown user");
        }
        repo.upsertShare(noteId, targetUserId, permission);

        User actor = users.getById(actorId);
        String noteUrl = baseUrl + "/notes/" + noteId;
        mailer.notifyNoteShared(target.email(), actor.displayName(), n.title(), noteUrl, permission == Permission.EDIT);

        return new UserShare(target.id(), target.displayName(), target.avatarUrl(), permission, Instant.now());
    }

    public void revokeShare(String noteId, String actorId, String targetUserId) {
        requireOwner(noteId, actorId);
        repo.deleteShare(noteId, targetUserId);
    }

    public record PublicShareResult(PublicShare share, String url) {}

    public PublicShareResult createPublicShare(String noteId, String actorId) {
        requireOwner(noteId, actorId);
        PublicShare ps = repo.createPublicShare(noteId);
        return new PublicShareResult(ps, baseUrl + "/shared/" + ps.token());
    }

    public void revokePublicShare(String noteId, String actorId) {
        requireOwner(noteId, actorId);
        repo.deletePublicShare(noteId);
    }

    public PublicNoteView getPublicNote(String token) {
        return repo.getByPublicToken(token);
    }
}
