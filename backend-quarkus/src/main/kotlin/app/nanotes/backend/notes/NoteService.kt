package app.nanotes.backend.notes

import app.nanotes.backend.apperr.ForbiddenException
import app.nanotes.backend.apperr.NotFoundException
import app.nanotes.backend.apperr.ValidationException
import app.nanotes.backend.apperr.VersionConflictException
import app.nanotes.backend.config.AppConfig
import app.nanotes.backend.mail.Mailer
import app.nanotes.backend.users.UserRepository
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant

@ApplicationScoped
class NoteService(
    private val repo: NoteRepository,
    private val users: UserRepository,
    private val mailer: Mailer,
    config: AppConfig,
) {
    /** Frontend origin, for links in emails and public share URLs. */
    private val baseUrl: String = config.frontendUrl

    companion object {
        const val INITIAL_PAGE_SIZE = 12
        private const val SAMPLE_NOTE_ID = "00000000-0000-0000-0000-000000000000"

        private fun sampleNote(): NoteSummary = NoteSummary(
            id = SAMPLE_NOTE_ID,
            title = "Welcome to NA Notes",
            contentMarkdown = "Sign in to create your own notes, share them with teammates, and mention people to loop them in.",
            ownerId = SAMPLE_NOTE_ID,
            myPermission = Permission.READ,
            isPublic = true,
            updatedAt = Instant.now(),
        )
    }

    /** The dashboard feed. An anonymous viewer (viewerId null/blank) always sees exactly the sample note. */
    fun list(viewerId: String?, cursor: String?, limit: Int): Page {
        if (viewerId.isNullOrEmpty()) {
            return Page(listOf(sampleNote()), null)
        }
        return repo.listForViewer(viewerId, cursor, if (limit <= 0) INITIAL_PAGE_SIZE else limit)
    }

    /**
     * Fetches a note for viewerId, resolving their effective permission. A
     * viewer with no ownership or share record gets NotFoundException rather
     * than ForbiddenException, so the endpoint doesn't reveal the note exists.
     */
    fun get(noteId: String, viewerId: String): Note {
        val n = repo.getById(noteId)
        if (n.ownerId == viewerId) {
            return n.copy(myPermission = Permission.OWNER)
        }
        val perm = repo.sharePermission(noteId, viewerId) ?: throw NotFoundException()
        return n.copy(myPermission = perm)
    }

    fun create(ownerId: String, title: String, content: String, mentionedUserIds: List<String>?): Note {
        if (title.isEmpty()) {
            throw ValidationException("title is required")
        }
        val n = repo.create(ownerId, title, content)
        notifyMentions(n, ownerId, emptySet(), mentionedUserIds)
        return n
    }

    fun update(
        noteId: String,
        actorId: String,
        expectedVersion: Int,
        title: String,
        content: String,
        mentionedUserIds: List<String>?,
    ): Note {
        if (title.isEmpty()) {
            throw ValidationException("title is required")
        }
        val current = get(noteId, actorId)
        if (current.myPermission != Permission.OWNER && current.myPermission != Permission.EDIT) {
            throw ForbiddenException()
        }

        val existingMentions = repo.existingMentions(noteId)

        val updated = try {
            repo.update(noteId, title, content, expectedVersion).copy(myPermission = current.myPermission)
        } catch (e: VersionConflictException) {
            throw VersionConflictException(e.currentNote.copy(myPermission = current.myPermission))
        }

        notifyMentions(updated, actorId, existingMentions, mentionedUserIds)
        return updated
    }

    /**
     * Records mentionedUserIds against the note and emails only the ones
     * not already present in alreadyMentioned, so editing a note doesn't
     * re-notify people mentioned in an earlier version.
     */
    private fun notifyMentions(n: Note, actorId: String, alreadyMentioned: Set<String>, mentionedUserIds: List<String>?) {
        if (mentionedUserIds.isNullOrEmpty()) return

        repo.addMentions(n.id, mentionedUserIds)
        val actor = users.getById(actorId)
        val noteUrl = "$baseUrl/notes/${n.id}"
        for (uid in mentionedUserIds) {
            if (uid in alreadyMentioned || uid == actorId) continue
            val mentioned = try {
                users.getById(uid)
            } catch (e: NotFoundException) {
                continue // unknown/invalid mention target: skip rather than fail the save
            }
            mailer.notifyMentioned(mentioned.email, actor.displayName, n.title, noteUrl)
        }
    }

    fun delete(noteId: String, actorId: String) {
        val n = repo.getById(noteId)
        if (n.ownerId != actorId) throw ForbiddenException()
        repo.delete(noteId)
    }

    private fun requireOwner(noteId: String, actorId: String): Note {
        val n = repo.getById(noteId)
        if (n.ownerId != actorId) throw ForbiddenException()
        return n
    }

    data class Shares(val userShares: List<UserShare>, val publicShare: PublicShare?)

    fun listShares(noteId: String, actorId: String): Shares {
        requireOwner(noteId, actorId)
        return Shares(repo.listShares(noteId), repo.getPublicShare(noteId))
    }

    fun shareWithUser(noteId: String, actorId: String, targetUserId: String, permission: Permission): UserShare {
        val n = requireOwner(noteId, actorId)
        if (targetUserId == actorId) {
            throw ValidationException("cannot share a note with yourself")
        }
        val target = try {
            users.getById(targetUserId)
        } catch (e: NotFoundException) {
            throw ValidationException("unknown user")
        }
        repo.upsertShare(noteId, targetUserId, permission)

        val actor = users.getById(actorId)
        val noteUrl = "$baseUrl/notes/$noteId"
        mailer.notifyNoteShared(target.email, actor.displayName, n.title, noteUrl, permission == Permission.EDIT)

        return UserShare(target.id, target.displayName, target.avatarUrl, permission, Instant.now())
    }

    fun revokeShare(noteId: String, actorId: String, targetUserId: String) {
        requireOwner(noteId, actorId)
        repo.deleteShare(noteId, targetUserId)
    }

    data class PublicShareResult(val share: PublicShare, val url: String)

    fun createPublicShare(noteId: String, actorId: String): PublicShareResult {
        requireOwner(noteId, actorId)
        val ps = repo.createPublicShare(noteId)
        return PublicShareResult(ps, "$baseUrl/shared/${ps.token}")
    }

    fun revokePublicShare(noteId: String, actorId: String) {
        requireOwner(noteId, actorId)
        repo.deletePublicShare(noteId)
    }

    fun getPublicNote(token: String): PublicNoteView = repo.getByPublicToken(token)
}
