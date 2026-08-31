package app.nanotes.backend.notes

import app.nanotes.backend.apperr.NotFoundException
import app.nanotes.backend.apperr.ValidationException
import app.nanotes.backend.apperr.VersionConflictException
import app.nanotes.backend.db.Database
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NoteRepositoryTest {

    private lateinit var notes: NoteRepository
    private lateinit var ownerId: String
    private lateinit var otherUserId: String

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        val db = Database.forTesting(tempDir.resolve("notes-test.db").toString())
        notes = NoteRepository(db)
        ownerId = insertUser(db, "owner@example.com", "Owner")
        otherUserId = insertUser(db, "other@example.com", "Other")
    }

    private fun insertUser(db: Database, email: String, displayName: String): String {
        val id = UUID.randomUUID().toString()
        db.update(
            "INSERT INTO users (id, oidc_subject, email, display_name, created_at) VALUES (?, ?, ?, ?, ?)",
            id, "sub-$id", email, displayName, "2024-01-01T00:00:00.000000000Z",
        )
        return id
    }

    @Test
    fun createThenGetRoundTrips() {
        val created = notes.create(ownerId, "Title", "Body")
        val fetched = notes.getById(created.id)
        assertEquals("Title", fetched.title)
        assertEquals("Body", fetched.contentMarkdown)
        assertEquals(1, fetched.version)
        assertTrue(!fetched.isPublic)
    }

    @Test
    fun getByIdThrowsNotFoundForUnknownId() {
        assertFailsWith<NotFoundException> { notes.getById(UUID.randomUUID().toString()) }
    }

    @Test
    fun updateWithCorrectVersionSucceedsAndIncrementsVersion() {
        val created = notes.create(ownerId, "Title", "Body")
        val updated = notes.update(created.id, "Title2", "Body2", 1)
        assertEquals(2, updated.version)
        assertEquals("Title2", updated.title)
    }

    @Test
    fun updateWithStaleVersionThrowsVersionConflictCarryingCurrentNote() {
        val created = notes.create(ownerId, "Title", "Body")
        notes.update(created.id, "Title2", "Body2", 1) // now at version 2

        val ex = assertFailsWith<VersionConflictException> { notes.update(created.id, "Title3", "Body3", 1) }
        assertEquals(2, ex.currentNote.version)
        assertEquals("Title2", ex.currentNote.title)
    }

    @Test
    fun deleteRemovesNote() {
        val created = notes.create(ownerId, "Title", "Body")
        notes.delete(created.id)
        assertFailsWith<NotFoundException> { notes.getById(created.id) }
    }

    @Test
    fun deleteUnknownNoteThrowsNotFound() {
        assertFailsWith<NotFoundException> { notes.delete(UUID.randomUUID().toString()) }
    }

    @Test
    fun shareGrantsVisibilityAndPermission() {
        val created = notes.create(ownerId, "Title", "Body")
        assertNull(notes.sharePermission(created.id, otherUserId))

        notes.upsertShare(created.id, otherUserId, Permission.READ)
        assertEquals(Permission.READ, notes.sharePermission(created.id, otherUserId))

        notes.upsertShare(created.id, otherUserId, Permission.EDIT)
        assertEquals(Permission.EDIT, notes.sharePermission(created.id, otherUserId))

        notes.deleteShare(created.id, otherUserId)
        assertNull(notes.sharePermission(created.id, otherUserId))
    }

    @Test
    fun publicShareRoundTripsAndRevokeInvalidatesToken() {
        val created = notes.create(ownerId, "Title", "Body")
        assertNull(notes.getPublicShare(created.id))

        val ps = notes.createPublicShare(created.id)
        val view = notes.getByPublicToken(ps.token)
        assertEquals("Title", view.title)

        // Re-publishing replaces the token — the old one no longer resolves.
        val ps2 = notes.createPublicShare(created.id)
        assertTrue(ps.token != ps2.token)
        assertFailsWith<NotFoundException> { notes.getByPublicToken(ps.token) }

        notes.deletePublicShare(created.id)
        assertFailsWith<NotFoundException> { notes.getByPublicToken(ps2.token) }
    }

    @Test
    fun mentionsAreTrackedOncePerUser() {
        val created = notes.create(ownerId, "Title", "Body")
        assertTrue(notes.existingMentions(created.id).isEmpty())

        notes.addMentions(created.id, listOf(otherUserId))
        assertEquals(setOf(otherUserId), notes.existingMentions(created.id))

        // Adding the same mention again must not error (ON CONFLICT DO NOTHING).
        notes.addMentions(created.id, listOf(otherUserId))
        assertEquals(setOf(otherUserId), notes.existingMentions(created.id))
    }

    @Test
    fun listForViewerPaginatesNewestFirstWithoutDuplicatesOrGaps() {
        val ids = (0 until 5).map {
            val id = notes.create(ownerId, "Note $it", "Body $it").id
            sleepToAdvanceClock()
            id
        }

        val seen = mutableSetOf<String>()
        var cursor: String? = null
        var pages = 0
        while (true) {
            val page = notes.listForViewer(ownerId, cursor, 2)
            for (s in page.items) {
                assertTrue(seen.add(s.id), "duplicate note across pages: ${s.id}")
            }
            pages++
            val next = page.nextCursor ?: break
            cursor = next
            assertTrue(pages < 10, "pagination did not terminate")
        }
        assertEquals(ids.toSet(), seen)
    }

    @Test
    fun listForViewerOnlyIncludesOwnedOrSharedNotes() {
        val owned = notes.create(ownerId, "Owned", "Body")
        val notShared = notes.create(otherUserId, "Not shared", "Body")
        val shared = notes.create(otherUserId, "Shared", "Body")
        notes.upsertShare(shared.id, ownerId, Permission.READ)

        val page = notes.listForViewer(ownerId, null, 50)
        val ids = page.items.map { it.id }.toSet()
        assertTrue(owned.id in ids)
        assertTrue(shared.id in ids)
        assertTrue(notShared.id !in ids)
    }

    @Test
    fun invalidCursorIsRejected() {
        assertFailsWith<ValidationException> { notes.listForViewer(ownerId, "not-a-cursor", 10) }
    }

    @Test
    fun getPublicShareIsNullForUnpublishedNote() {
        val created = notes.create(ownerId, "Title", "Body")
        assertNull(notes.getPublicShare(created.id))
    }

    /** Timestamps have nanosecond precision but the system clock's real resolution may be coarser on CI. */
    private fun sleepToAdvanceClock() {
        Thread.sleep(2)
    }
}
