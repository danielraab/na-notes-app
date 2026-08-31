// DTOs mirror the schemas in /openapi/openapi.yaml exactly (field names,
// casing, nullability) — that file is the source of truth; if these
// diverge from it, the spec is wrong or this code is.
package app.nanotes.backend.web.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class ErrorDto(val error: Detail) {
    data class Detail(val code: String, val message: String)

    companion object {
        fun of(code: String, message: String): ErrorDto = ErrorDto(Detail(code, message))
    }
}

data class UserDto(val id: String, val email: String, val displayName: String, val avatarUrl: String?)

data class UserSummaryDto(val id: String, val displayName: String, val avatarUrl: String?)

data class NoteInputDto(val title: String, val contentMarkdown: String, val mentionedUserIds: List<String>? = null)

// isPublic is annotated explicitly: Kotlin compiles `val isPublic: Boolean`
// to a getter named `isPublic()`, and Jackson's default bean-property
// naming then strips the leading "is" the way it would for a Java boolean
// getter, serializing the field as "public" instead of "isPublic" — which
// would silently break the openapi.yaml contract. @get:JsonProperty pins
// the wire name explicitly rather than relying on that inference.
data class NoteDto(
    val id: String,
    val title: String,
    val contentMarkdown: String,
    val ownerId: String,
    val version: Int,
    val myPermission: String,
    @get:JsonProperty("isPublic") val isPublic: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class NoteSummaryDto(
    val id: String,
    val title: String,
    val contentMarkdown: String,
    val ownerId: String,
    val myPermission: String,
    @get:JsonProperty("isPublic") val isPublic: Boolean,
    val updatedAt: Instant,
)

data class NotePageDto(val items: List<NoteSummaryDto>, val nextCursor: String?)

data class UserShareDto(val user: UserSummaryDto, val permission: String, val createdAt: Instant)

data class PublicShareDto(val token: String, val url: String, val createdAt: Instant)

data class PublicNoteViewDto(val title: String, val contentMarkdown: String, val updatedAt: Instant)

data class SharesDto(val userShares: List<UserShareDto>, val publicShare: PublicShareDto?)

data class CreateShareRequestDto(val userId: String, val permission: String)
