package app.nanotes.backend.web

import app.nanotes.backend.apperr.ValidationException
import app.nanotes.backend.users.UserRepository
import app.nanotes.backend.web.dto.UserSummaryDto
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam

@Path("/api/users")
class UsersResource(private val users: UserRepository, private val currentSession: CurrentSession) {

    @GET
    @Path("/search")
    fun search(@QueryParam("q") q: String?, @QueryParam("limit") limitParam: String?): List<UserSummaryDto> {
        val userId = currentSession.requireUserId()
        if (q.isNullOrEmpty()) {
            throw ValidationException("q is required")
        }

        var limit = DEFAULT_LIMIT
        if (!limitParam.isNullOrEmpty()) {
            limit = QueryParams.parsePositiveInt(limitParam, "limit must be a positive integer")
        }
        limit = minOf(limit, MAX_LIMIT)

        return users.search(userId, q, limit).map(Dtos::toUserSummaryDto)
    }

    companion object {
        private const val MAX_LIMIT = 25
        private const val DEFAULT_LIMIT = 10
    }
}
