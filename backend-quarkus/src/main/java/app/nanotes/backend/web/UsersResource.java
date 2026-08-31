package app.nanotes.backend.web;

import app.nanotes.backend.apperr.ValidationException;
import app.nanotes.backend.users.UserRepository;
import app.nanotes.backend.web.dto.UserSummaryDto;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.List;

@Path("/api/users")
public class UsersResource {

    private static final int MAX_LIMIT = 25;
    private static final int DEFAULT_LIMIT = 10;

    private final UserRepository users;
    private final CurrentSession currentSession;

    public UsersResource(UserRepository users, CurrentSession currentSession) {
        this.users = users;
        this.currentSession = currentSession;
    }

    @GET
    @Path("/search")
    public List<UserSummaryDto> search(@QueryParam("q") String q, @QueryParam("limit") String limitParam) {
        String userId = currentSession.requireUserId();
        if (q == null || q.isEmpty()) {
            throw new ValidationException("q is required");
        }

        int limit = DEFAULT_LIMIT;
        if (limitParam != null && !limitParam.isEmpty()) {
            limit = QueryParams.parsePositiveInt(limitParam, "limit must be a positive integer");
        }
        limit = Math.min(limit, MAX_LIMIT);

        return users.search(userId, q, limit).stream().map(Dtos::toUserSummaryDto).toList();
    }
}
