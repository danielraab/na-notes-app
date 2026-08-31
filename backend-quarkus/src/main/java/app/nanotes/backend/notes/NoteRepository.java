package app.nanotes.backend.notes;

import app.nanotes.backend.apperr.NotFoundException;
import app.nanotes.backend.apperr.VersionConflictException;
import app.nanotes.backend.db.Database;
import app.nanotes.backend.db.Timestamps;
import app.nanotes.backend.randtoken.RandToken;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class NoteRepository {

    /** 128 bits of entropy, see ADR 0009. */
    private static final int PUBLIC_SHARE_TOKEN_BYTES = 16;

    private final Database db;

    public NoteRepository(Database db) {
        this.db = db;
    }

    public Note create(String ownerId, String title, String content) {
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        db.update(
                """
                INSERT INTO notes (id, owner_id, title, content_markdown, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, ?, ?)
                """,
                id, ownerId, title, content, Timestamps.format(now), Timestamps.format(now));
        return new Note(id, ownerId, title, content, 1, false, now, now, Permission.OWNER);
    }

    /** Fetches the raw note without regard to who is asking; callers are responsible for authorization decisions. */
    public Note getById(String id) {
        return db.queryOne(
                        """
                        SELECT n.id, n.owner_id, n.title, n.content_markdown, n.version, n.created_at, n.updated_at,
                               nps.note_id AS public_note_id
                        FROM notes n
                        LEFT JOIN note_public_shares nps ON nps.note_id = n.id
                        WHERE n.id = ?
                        """,
                        NoteRepository::mapNote,
                        id)
                .orElseThrow(NotFoundException::new);
    }

    private static Note mapNote(ResultSet rs) throws SQLException {
        boolean isPublic = rs.getString("public_note_id") != null;
        return new Note(
                rs.getString("id"),
                rs.getString("owner_id"),
                rs.getString("title"),
                rs.getString("content_markdown"),
                rs.getInt("version"),
                isPublic,
                Timestamps.parse(rs.getString("created_at")),
                Timestamps.parse(rs.getString("updated_at")),
                null);
    }

    /** Applies an optimistic-concurrency-checked edit (ADR 0008): only succeeds if the row's version still matches. */
    public Note update(String id, String title, String content, int expectedVersion) {
        String now = Timestamps.now();
        int affected = db.update(
                "UPDATE notes SET title = ?, content_markdown = ?, version = version + 1, updated_at = ? WHERE id = ? AND version = ?",
                title, content, now, id, expectedVersion);
        if (affected == 0) {
            // not-found takes precedence over conflict
            Note current = getById(id);
            throw new VersionConflictException(current);
        }
        return getById(id);
    }

    public void delete(String id) {
        int affected = db.update("DELETE FROM notes WHERE id = ?", id);
        if (affected == 0) {
            throw new NotFoundException();
        }
    }

    /** The explicit share permission granted to userId on noteId, if any. Does not consider ownership. */
    public Optional<Permission> sharePermission(String noteId, String userId) {
        return db.queryOne(
                "SELECT permission FROM note_shares WHERE note_id = ? AND user_id = ?",
                rs -> Permission.fromWireValue(rs.getString("permission")),
                noteId, userId);
    }

    /** A cursor page of notes owned by, or shared with, userId, newest-edited first (ADR 0007). */
    public Page listForViewer(String userId, String cursor, int limit) {
        StringBuilder query = new StringBuilder(
                """
                SELECT n.id, n.title, n.content_markdown, n.owner_id, n.updated_at,
                       CASE WHEN n.owner_id = ? THEN 'owner' ELSE ns.permission END AS permission,
                       CASE WHEN nps.note_id IS NOT NULL THEN 1 ELSE 0 END AS is_public
                FROM notes n
                LEFT JOIN note_shares ns ON ns.note_id = n.id AND ns.user_id = ?
                LEFT JOIN note_public_shares nps ON nps.note_id = n.id
                WHERE (n.owner_id = ? OR ns.user_id = ?)
                """);
        Object[] args;
        if (cursor != null && !cursor.isEmpty()) {
            Cursor c = Cursor.decode(cursor);
            query.append("AND (n.updated_at, n.id) < (?, ?) ");
            args = new Object[] {userId, userId, userId, userId, c.updatedAt, c.id, limit + 1};
        } else {
            args = new Object[] {userId, userId, userId, userId, limit + 1};
        }
        query.append("ORDER BY n.updated_at DESC, n.id DESC LIMIT ?");

        List<NoteSummary> rows = db.query(
                query.toString(),
                rs -> new NoteSummary(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("content_markdown"),
                        rs.getString("owner_id"),
                        Permission.fromWireValue(rs.getString("permission")),
                        rs.getInt("is_public") == 1,
                        Timestamps.parse(rs.getString("updated_at"))),
                args);

        if (rows.size() > limit) {
            NoteSummary last = rows.get(limit - 1);
            return new Page(rows.subList(0, limit), Cursor.encode(last.updatedAt(), last.id()));
        }
        return new Page(rows, null);
    }

    public List<UserShare> listShares(String noteId) {
        return db.query(
                """
                SELECT u.id, u.display_name, u.avatar_url, ns.permission, ns.created_at
                FROM note_shares ns
                JOIN users u ON u.id = ns.user_id
                WHERE ns.note_id = ?
                ORDER BY ns.created_at
                """,
                rs -> new UserShare(
                        rs.getString("id"),
                        rs.getString("display_name"),
                        rs.getString("avatar_url"),
                        Permission.fromWireValue(rs.getString("permission")),
                        Timestamps.parse(rs.getString("created_at"))),
                noteId);
    }

    /**
     * Grants (or changes the permission of) userId's access to noteId. The
     * owner explicitly re-sharing an already-shared note still triggers a
     * notification email (see NoteService.shareWithUser) — a deliberate
     * simplification over tracking new-vs-changed shares.
     */
    public void upsertShare(String noteId, String userId, Permission permission) {
        db.update(
                """
                INSERT INTO note_shares (note_id, user_id, permission, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(note_id, user_id) DO UPDATE SET permission = excluded.permission
                """,
                noteId, userId, permission.wireValue(), Timestamps.now());
    }

    public void deleteShare(String noteId, String userId) {
        int affected = db.update("DELETE FROM note_shares WHERE note_id = ? AND user_id = ?", noteId, userId);
        if (affected == 0) {
            throw new NotFoundException();
        }
    }

    public Optional<PublicShare> getPublicShare(String noteId) {
        return db.queryOne(
                "SELECT token, created_at FROM note_public_shares WHERE note_id = ?",
                rs -> new PublicShare(rs.getString("token"), Timestamps.parse(rs.getString("created_at"))),
                noteId);
    }

    /** (Re)publishes noteId with a freshly generated, unguessable token, replacing any previous token (ADR 0009). */
    public PublicShare createPublicShare(String noteId) {
        String token = RandToken.generate(PUBLIC_SHARE_TOKEN_BYTES);
        Instant now = Instant.now();
        db.update(
                """
                INSERT INTO note_public_shares (note_id, token, created_at) VALUES (?, ?, ?)
                ON CONFLICT(note_id) DO UPDATE SET token = excluded.token, created_at = excluded.created_at
                """,
                noteId, token, Timestamps.format(now));
        return new PublicShare(token, now);
    }

    public void deletePublicShare(String noteId) {
        int affected = db.update("DELETE FROM note_public_shares WHERE note_id = ?", noteId);
        if (affected == 0) {
            throw new NotFoundException();
        }
    }

    public PublicNoteView getByPublicToken(String token) {
        return db.queryOne(
                        """
                        SELECT n.title, n.content_markdown, n.updated_at
                        FROM note_public_shares nps
                        JOIN notes n ON n.id = nps.note_id
                        WHERE nps.token = ?
                        """,
                        rs -> new PublicNoteView(
                                rs.getString("title"),
                                rs.getString("content_markdown"),
                                Timestamps.parse(rs.getString("updated_at"))),
                        token)
                .orElseThrow(NotFoundException::new);
    }

    /** The set of user IDs already recorded as mentioned in noteId, so the caller can notify only newly added mentions. */
    public Set<String> existingMentions(String noteId) {
        return new LinkedHashSet<>(db.query("SELECT user_id FROM note_mentions WHERE note_id = ?", rs -> rs.getString("user_id"), noteId));
    }

    public void addMentions(String noteId, List<String> userIds) {
        String now = Timestamps.now();
        for (String userId : userIds) {
            db.update(
                    "INSERT INTO note_mentions (note_id, user_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                    noteId, userId, now);
        }
    }
}
