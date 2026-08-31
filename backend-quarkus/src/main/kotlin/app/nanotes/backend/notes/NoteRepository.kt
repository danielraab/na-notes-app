package app.nanotes.backend.notes

import app.nanotes.backend.apperr.NotFoundException
import app.nanotes.backend.apperr.VersionConflictException
import app.nanotes.backend.db.Database
import app.nanotes.backend.db.Timestamps
import app.nanotes.backend.randtoken.RandToken
import jakarta.enterprise.context.ApplicationScoped
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class NoteRepository(private val db: Database) {

    companion object {
        /** 128 bits of entropy, see ADR 0009. */
        private const val PUBLIC_SHARE_TOKEN_BYTES = 16
    }

    fun create(ownerId: String, title: String, content: String): Note {
        val now = Instant.now()
        val id = UUID.randomUUID().toString()
        db.update(
            """
            INSERT INTO notes (id, owner_id, title, content_markdown, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, 1, ?, ?)
            """.trimIndent(),
            id, ownerId, title, content, Timestamps.format(now), Timestamps.format(now),
        )
        return Note(id, ownerId, title, content, 1, false, now, now, Permission.OWNER)
    }

    private fun mapNote(rs: ResultSet): Note {
        val isPublic = rs.getString("public_note_id") != null
        return Note(
            id = rs.getString("id"),
            ownerId = rs.getString("owner_id"),
            title = rs.getString("title"),
            contentMarkdown = rs.getString("content_markdown"),
            version = rs.getInt("version"),
            isPublic = isPublic,
            createdAt = Timestamps.parse(rs.getString("created_at")),
            updatedAt = Timestamps.parse(rs.getString("updated_at")),
            myPermission = null,
        )
    }

    /** Fetches the raw note without regard to who is asking; callers are responsible for authorization decisions. */
    fun getById(id: String): Note =
        db.queryOne(
            """
            SELECT n.id, n.owner_id, n.title, n.content_markdown, n.version, n.created_at, n.updated_at,
                   nps.note_id AS public_note_id
            FROM notes n
            LEFT JOIN note_public_shares nps ON nps.note_id = n.id
            WHERE n.id = ?
            """.trimIndent(),
            ::mapNote,
            id,
        ) ?: throw NotFoundException()

    /** Applies an optimistic-concurrency-checked edit (ADR 0008): only succeeds if the row's version still matches. */
    fun update(id: String, title: String, content: String, expectedVersion: Int): Note {
        val now = Timestamps.now()
        val affected = db.update(
            "UPDATE notes SET title = ?, content_markdown = ?, version = version + 1, updated_at = ? WHERE id = ? AND version = ?",
            title, content, now, id, expectedVersion,
        )
        if (affected == 0) {
            // not-found takes precedence over conflict
            throw VersionConflictException(getById(id))
        }
        return getById(id)
    }

    fun delete(id: String) {
        if (db.update("DELETE FROM notes WHERE id = ?", id) == 0) throw NotFoundException()
    }

    /** The explicit share permission granted to userId on noteId, if any. Does not consider ownership. */
    fun sharePermission(noteId: String, userId: String): Permission? =
        db.queryOne(
            "SELECT permission FROM note_shares WHERE note_id = ? AND user_id = ?",
            { rs -> Permission.fromWireValue(rs.getString("permission")) },
            noteId, userId,
        )

    /** A cursor page of notes owned by, or shared with, userId, newest-edited first (ADR 0007). */
    fun listForViewer(userId: String, cursor: String?, limit: Int): Page {
        val query = StringBuilder(
            """
            SELECT n.id, n.title, n.content_markdown, n.owner_id, n.updated_at,
                   CASE WHEN n.owner_id = ? THEN 'owner' ELSE ns.permission END AS permission,
                   CASE WHEN nps.note_id IS NOT NULL THEN 1 ELSE 0 END AS is_public
            FROM notes n
            LEFT JOIN note_shares ns ON ns.note_id = n.id AND ns.user_id = ?
            LEFT JOIN note_public_shares nps ON nps.note_id = n.id
            WHERE (n.owner_id = ? OR ns.user_id = ?)

            """.trimIndent(),
        )
        val args: Array<Any?>
        if (!cursor.isNullOrEmpty()) {
            val c = Cursor.decode(cursor)
            query.append("AND (n.updated_at, n.id) < (?, ?) ")
            args = arrayOf(userId, userId, userId, userId, c.updatedAt, c.id, limit + 1)
        } else {
            args = arrayOf(userId, userId, userId, userId, limit + 1)
        }
        query.append("ORDER BY n.updated_at DESC, n.id DESC LIMIT ?")

        val rows = db.query(
            query.toString(),
            { rs ->
                NoteSummary(
                    id = rs.getString("id"),
                    title = rs.getString("title"),
                    contentMarkdown = rs.getString("content_markdown"),
                    ownerId = rs.getString("owner_id"),
                    myPermission = Permission.fromWireValue(rs.getString("permission")),
                    isPublic = rs.getInt("is_public") == 1,
                    updatedAt = Timestamps.parse(rs.getString("updated_at")),
                )
            },
            *args,
        )

        return if (rows.size > limit) {
            val last = rows[limit - 1]
            Page(rows.subList(0, limit), Cursor.encode(last.updatedAt, last.id))
        } else {
            Page(rows, null)
        }
    }

    fun listShares(noteId: String): List<UserShare> =
        db.query(
            """
            SELECT u.id, u.display_name, u.avatar_url, ns.permission, ns.created_at
            FROM note_shares ns
            JOIN users u ON u.id = ns.user_id
            WHERE ns.note_id = ?
            ORDER BY ns.created_at
            """.trimIndent(),
            { rs ->
                UserShare(
                    userId = rs.getString("id"),
                    displayName = rs.getString("display_name"),
                    avatarUrl = rs.getString("avatar_url"),
                    permission = Permission.fromWireValue(rs.getString("permission")),
                    createdAt = Timestamps.parse(rs.getString("created_at")),
                )
            },
            noteId,
        )

    /**
     * Grants (or changes the permission of) userId's access to noteId. The
     * owner explicitly re-sharing an already-shared note still triggers a
     * notification email (see NoteService.shareWithUser) — a deliberate
     * simplification over tracking new-vs-changed shares.
     */
    fun upsertShare(noteId: String, userId: String, permission: Permission) {
        db.update(
            """
            INSERT INTO note_shares (note_id, user_id, permission, created_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(note_id, user_id) DO UPDATE SET permission = excluded.permission
            """.trimIndent(),
            noteId, userId, permission.wireValue, Timestamps.now(),
        )
    }

    fun deleteShare(noteId: String, userId: String) {
        if (db.update("DELETE FROM note_shares WHERE note_id = ? AND user_id = ?", noteId, userId) == 0) throw NotFoundException()
    }

    fun getPublicShare(noteId: String): PublicShare? =
        db.queryOne(
            "SELECT token, created_at FROM note_public_shares WHERE note_id = ?",
            { rs -> PublicShare(rs.getString("token"), Timestamps.parse(rs.getString("created_at"))) },
            noteId,
        )

    /** (Re)publishes noteId with a freshly generated, unguessable token, replacing any previous token (ADR 0009). */
    fun createPublicShare(noteId: String): PublicShare {
        val token = RandToken.generate(PUBLIC_SHARE_TOKEN_BYTES)
        val now = Instant.now()
        db.update(
            """
            INSERT INTO note_public_shares (note_id, token, created_at) VALUES (?, ?, ?)
            ON CONFLICT(note_id) DO UPDATE SET token = excluded.token, created_at = excluded.created_at
            """.trimIndent(),
            noteId, token, Timestamps.format(now),
        )
        return PublicShare(token, now)
    }

    fun deletePublicShare(noteId: String) {
        if (db.update("DELETE FROM note_public_shares WHERE note_id = ?", noteId) == 0) throw NotFoundException()
    }

    fun getByPublicToken(token: String): PublicNoteView =
        db.queryOne(
            """
            SELECT n.title, n.content_markdown, n.updated_at
            FROM note_public_shares nps
            JOIN notes n ON n.id = nps.note_id
            WHERE nps.token = ?
            """.trimIndent(),
            { rs -> PublicNoteView(rs.getString("title"), rs.getString("content_markdown"), Timestamps.parse(rs.getString("updated_at"))) },
            token,
        ) ?: throw NotFoundException()

    /** The set of user IDs already recorded as mentioned in noteId, so the caller can notify only newly added mentions. */
    fun existingMentions(noteId: String): Set<String> =
        db.query("SELECT user_id FROM note_mentions WHERE note_id = ?", { rs -> rs.getString("user_id") }, noteId).toSet()

    fun addMentions(noteId: String, userIds: List<String>) {
        val now = Timestamps.now()
        for (userId in userIds) {
            db.update(
                "INSERT INTO note_mentions (note_id, user_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                noteId, userId, now,
            )
        }
    }
}
