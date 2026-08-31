package app.nanotes.backend.users

import app.nanotes.backend.apperr.NotFoundException
import app.nanotes.backend.db.Database
import app.nanotes.backend.db.Timestamps
import jakarta.enterprise.context.ApplicationScoped
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/** Manages user accounts. Accounts are created lazily on first successful OIDC login; there is no separate registration flow. */
@ApplicationScoped
class UserRepository(private val db: Database) {

    private fun mapUser(rs: ResultSet): User = User(
        id = rs.getString("id"),
        email = rs.getString("email"),
        displayName = rs.getString("display_name"),
        avatarUrl = rs.getString("avatar_url"),
        createdAt = Timestamps.parse(rs.getString("created_at")),
    )

    /**
     * Creates the user on first login, or refreshes their profile fields
     * (display name/avatar can change at the identity provider) on
     * subsequent logins. Matching is on the stable OIDC subject, never on
     * email alone, since some providers allow email reuse/change.
     */
    fun upsertFromOidc(subject: String, email: String, displayName: String, avatarUrl: String?): User {
        val existing = db.queryOne(
            "SELECT id, email, display_name, avatar_url, created_at FROM users WHERE oidc_subject = ?",
            ::mapUser,
            subject,
        )

        val normalizedAvatar = avatarUrl?.takeIf { it.isNotEmpty() }

        if (existing == null) {
            val u = User(UUID.randomUUID().toString(), email, displayName, normalizedAvatar, Instant.now())
            db.update(
                "INSERT INTO users (id, oidc_subject, email, display_name, avatar_url, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                u.id, subject, u.email, u.displayName, u.avatarUrl, Timestamps.format(u.createdAt),
            )
            return u
        }

        db.update(
            "UPDATE users SET email = ?, display_name = ?, avatar_url = ? WHERE id = ?",
            email, displayName, normalizedAvatar, existing.id,
        )
        return existing.copy(email = email, displayName = displayName, avatarUrl = normalizedAvatar)
    }

    fun getById(id: String): User =
        db.queryOne(
            "SELECT id, email, display_name, avatar_url, created_at FROM users WHERE id = ?",
            ::mapUser,
            id,
        ) ?: throw NotFoundException()

    /** Users whose display name or email starts with [q], excluding the caller, for mention/share autocomplete. */
    fun search(excludeUserId: String, q: String, limit: Int): List<UserSummary> {
        val like = q.lowercase() + "%"
        return db.query(
            """
            SELECT id, display_name, avatar_url FROM users
            WHERE id != ? AND (LOWER(display_name) LIKE ? OR LOWER(email) LIKE ?)
            ORDER BY display_name LIMIT ?
            """.trimIndent(),
            { rs -> UserSummary(rs.getString("id"), rs.getString("display_name"), rs.getString("avatar_url")) },
            excludeUserId, like, like, limit,
        )
    }
}
