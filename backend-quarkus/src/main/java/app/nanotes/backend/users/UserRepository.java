package app.nanotes.backend.users;

import app.nanotes.backend.apperr.NotFoundException;
import app.nanotes.backend.db.Database;
import app.nanotes.backend.db.Timestamps;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages user accounts. Accounts are created lazily on first successful
 * OIDC login; there is no separate registration flow.
 */
@ApplicationScoped
public class UserRepository {

    private final Database db;

    public UserRepository(Database db) {
        this.db = db;
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                Timestamps.parse(rs.getString("created_at")));
    }

    /**
     * Creates the user on first login, or refreshes their profile fields
     * (display name/avatar can change at the identity provider) on
     * subsequent logins. Matching is on the stable OIDC subject, never on
     * email alone, since some providers allow email reuse/change.
     */
    public User upsertFromOidc(String subject, String email, String displayName, String avatarUrl) {
        Optional<User> existing = db.queryOne(
                "SELECT id, email, display_name, avatar_url, created_at FROM users WHERE oidc_subject = ?",
                UserRepository::mapUser,
                subject);

        String normalizedAvatar = (avatarUrl == null || avatarUrl.isEmpty()) ? null : avatarUrl;

        if (existing.isEmpty()) {
            User u = new User(UUID.randomUUID().toString(), email, displayName, normalizedAvatar, Instant.now());
            db.update(
                    "INSERT INTO users (id, oidc_subject, email, display_name, avatar_url, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    u.id(), subject, u.email(), u.displayName(), u.avatarUrl(), Timestamps.format(u.createdAt()));
            return u;
        }

        User u = existing.get();
        db.update(
                "UPDATE users SET email = ?, display_name = ?, avatar_url = ? WHERE id = ?",
                email, displayName, normalizedAvatar, u.id());
        return new User(u.id(), email, displayName, normalizedAvatar, u.createdAt());
    }

    public User getById(String id) {
        return db.queryOne(
                        "SELECT id, email, display_name, avatar_url, created_at FROM users WHERE id = ?",
                        UserRepository::mapUser,
                        id)
                .orElseThrow(NotFoundException::new);
    }

    /** Users whose display name or email starts with {@code q}, excluding the caller, for mention/share autocomplete. */
    public List<UserSummary> search(String excludeUserId, String q, int limit) {
        String like = q.toLowerCase() + "%";
        return db.query(
                """
                SELECT id, display_name, avatar_url FROM users
                WHERE id != ? AND (LOWER(display_name) LIKE ? OR LOWER(email) LIKE ?)
                ORDER BY display_name LIMIT ?
                """,
                rs -> new UserSummary(rs.getString("id"), rs.getString("display_name"), rs.getString("avatar_url")),
                excludeUserId, like, like, limit);
    }
}
