package app.nanotes.backend.web

import app.nanotes.backend.notes.Note
import app.nanotes.backend.notes.NoteSummary
import app.nanotes.backend.notes.Page
import app.nanotes.backend.notes.PublicNoteView
import app.nanotes.backend.notes.PublicShare
import app.nanotes.backend.notes.UserShare
import app.nanotes.backend.users.User
import app.nanotes.backend.users.UserSummary
import app.nanotes.backend.web.dto.NoteDto
import app.nanotes.backend.web.dto.NotePageDto
import app.nanotes.backend.web.dto.NoteSummaryDto
import app.nanotes.backend.web.dto.PublicNoteViewDto
import app.nanotes.backend.web.dto.PublicShareDto
import app.nanotes.backend.web.dto.UserDto
import app.nanotes.backend.web.dto.UserShareDto
import app.nanotes.backend.web.dto.UserSummaryDto

/** Maps domain model objects onto the DTOs in [app.nanotes.backend.web.dto]. */
object Dtos {
    fun toUserDto(u: User): UserDto = UserDto(u.id, u.email, u.displayName, u.avatarUrl)

    fun toUserSummaryDto(u: UserSummary): UserSummaryDto = UserSummaryDto(u.id, u.displayName, u.avatarUrl)

    fun toNoteDto(n: Note): NoteDto {
        // By the time a Note reaches the web layer, NoteService has always
        // resolved the caller's permission onto it (raw repository fetches
        // never leave this package with myPermission still null).
        val permission = checkNotNull(n.myPermission) { "Note ${n.id} reached the web layer without myPermission resolved" }
        return NoteDto(
            id = n.id,
            title = n.title,
            contentMarkdown = n.contentMarkdown,
            ownerId = n.ownerId,
            version = n.version,
            myPermission = permission.wireValue,
            isPublic = n.isPublic,
            createdAt = n.createdAt,
            updatedAt = n.updatedAt,
        )
    }

    fun toNoteSummaryDto(s: NoteSummary): NoteSummaryDto = NoteSummaryDto(
        id = s.id,
        title = s.title,
        contentMarkdown = s.contentMarkdown,
        ownerId = s.ownerId,
        myPermission = s.myPermission.wireValue,
        isPublic = s.isPublic,
        updatedAt = s.updatedAt,
    )

    fun toNotePageDto(p: Page): NotePageDto = NotePageDto(p.items.map(::toNoteSummaryDto), p.nextCursor)

    fun toUserShareDto(s: UserShare): UserShareDto {
        val user = UserSummaryDto(s.userId, s.displayName, s.avatarUrl)
        return UserShareDto(user, s.permission.wireValue, s.createdAt)
    }

    fun toPublicShareDto(ps: PublicShare, url: String): PublicShareDto = PublicShareDto(ps.token, url, ps.createdAt)

    fun toPublicNoteViewDto(v: PublicNoteView): PublicNoteViewDto = PublicNoteViewDto(v.title, v.contentMarkdown, v.updatedAt)
}
