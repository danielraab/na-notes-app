package app.nanotes.backend.notes

import java.time.Instant

enum class Permission(val wireValue: String) {
    OWNER("owner"),
    EDIT("edit"),
    READ("read");

    companion object {
        fun fromWireValue(v: String): Permission =
            entries.find { it.wireValue == v } ?: throw IllegalArgumentException("unknown permission: $v")
    }
}

data class Note(
    val id: String,
    val ownerId: String,
    val title: String,
    val contentMarkdown: String,
    val version: Int,
    val isPublic: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val myPermission: Permission?,
)

data class NoteSummary(
    val id: String,
    val title: String,
    val contentMarkdown: String,
    val ownerId: String,
    val myPermission: Permission,
    val isPublic: Boolean,
    val updatedAt: Instant,
)

/** [nextCursor] is null when there are no more pages. */
data class Page(val items: List<NoteSummary>, val nextCursor: String?)

data class UserShare(val userId: String, val displayName: String, val avatarUrl: String?, val permission: Permission, val createdAt: Instant)

data class PublicShare(val token: String, val createdAt: Instant)

data class PublicNoteView(val title: String, val contentMarkdown: String, val updatedAt: Instant)
