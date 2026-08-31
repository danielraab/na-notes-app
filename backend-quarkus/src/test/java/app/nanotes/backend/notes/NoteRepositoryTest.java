package app.nanotes.backend.notes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.nanotes.backend.apperr.NotFoundException;
import app.nanotes.backend.apperr.VersionConflictException;
import app.nanotes.backend.db.Database;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NoteRepositoryTest {

    private NoteRepository notes;
    private String ownerId;
    private String otherUserId;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Database db = Database.forTesting(tempDir.resolve("notes-test.db").toString());
        notes = new NoteRepository(db);
        ownerId = insertUser(db, "owner@example.com", "Owner");
        otherUserId = insertUser(db, "other@example.com", "Other");
    }

    private static String insertUser(Database db, String email, String displayName) {
        String id = UUID.randomUUID().toString();
        db.update(
                "INSERT INTO users (id, oidc_subject, email, display_name, created_at) VALUES (?, ?, ?, ?, ?)",
                id, "sub-" + id, email, displayName, "2024-01-01T00:00:00.000000000Z");
        return id;
    }

    @Test
    void createThenGetRoundTrips() {
        Note created = notes.create(ownerId, "Title", "Body");
        Note fetched = notes.getById(created.id());
        assertEquals("Title", fetched.title());
        assertEquals("Body", fetched.contentMarkdown());
        assertEquals(1, fetched.version());
        assertTrue(!fetched.isPublic());
    }

    @Test
    void getByIdThrowsNotFoundForUnknownId() {
        assertThrows(NotFoundException.class, () -> notes.getById(UUID.randomUUID().toString()));
    }

    @Test
    void updateWithCorrectVersionSucceedsAndIncrementsVersion() {
        Note created = notes.create(ownerId, "Title", "Body");
        Note updated = notes.update(created.id(), "Title2", "Body2", 1);
        assertEquals(2, updated.version());
        assertEquals("Title2", updated.title());
    }

    @Test
    void updateWithStaleVersionThrowsVersionConflictCarryingCurrentNote() {
        Note created = notes.create(ownerId, "Title", "Body");
        notes.update(created.id(), "Title2", "Body2", 1); // now at version 2

        VersionConflictException ex =
                assertThrows(VersionConflictException.class, () -> notes.update(created.id(), "Title3", "Body3", 1));
        assertEquals(2, ex.currentNote().version());
        assertEquals("Title2", ex.currentNote().title());
    }

    @Test
    void deleteRemovesNote() {
        Note created = notes.create(ownerId, "Title", "Body");
        notes.delete(created.id());
        assertThrows(NotFoundException.class, () -> notes.getById(created.id()));
    }

    @Test
    void deleteUnknownNoteThrowsNotFound() {
        assertThrows(NotFoundException.class, () -> notes.delete(UUID.randomUUID().toString()));
    }

    @Test
    void shareGrantsVisibilityAndPermission() {
        Note created = notes.create(ownerId, "Title", "Body");
        assertEquals(java.util.Optional.empty(), notes.sharePermission(created.id(), otherUserId));

        notes.upsertShare(created.id(), otherUserId, Permission.READ);
        assertEquals(Permission.READ, notes.sharePermission(created.id(), otherUserId).orElseThrow());

        notes.upsertShare(created.id(), otherUserId, Permission.EDIT);
        assertEquals(Permission.EDIT, notes.sharePermission(created.id(), otherUserId).orElseThrow());

        notes.deleteShare(created.id(), otherUserId);
        assertEquals(java.util.Optional.empty(), notes.sharePermission(created.id(), otherUserId));
    }

    @Test
    void publicShareRoundTripsAndRevokeInvalidatesToken() {
        Note created = notes.create(ownerId, "Title", "Body");
        assertEquals(java.util.Optional.empty(), notes.getPublicShare(created.id()));

        PublicShare ps = notes.createPublicShare(created.id());
        PublicNoteView view = notes.getByPublicToken(ps.token());
        assertEquals("Title", view.title());

        // Re-publishing replaces the token — the old one no longer resolves.
        PublicShare ps2 = notes.createPublicShare(created.id());
        assertTrue(!ps.token().equals(ps2.token()));
        assertThrows(NotFoundException.class, () -> notes.getByPublicToken(ps.token()));

        notes.deletePublicShare(created.id());
        assertThrows(NotFoundException.class, () -> notes.getByPublicToken(ps2.token()));
    }

    @Test
    void mentionsAreTrackedOncePerUser() {
        Note created = notes.create(ownerId, "Title", "Body");
        assertTrue(notes.existingMentions(created.id()).isEmpty());

        notes.addMentions(created.id(), List.of(otherUserId));
        assertEquals(Set.of(otherUserId), notes.existingMentions(created.id()));

        // Adding the same mention again must not error (ON CONFLICT DO NOTHING).
        notes.addMentions(created.id(), List.of(otherUserId));
        assertEquals(Set.of(otherUserId), notes.existingMentions(created.id()));
    }

    @Test
    void listForViewerPaginatesNewestFirstWithoutDuplicatesOrGaps() {
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(notes.create(ownerId, "Note " + i, "Body " + i).id());
            sleepToAdvanceClock();
        }

        Set<String> seen = new HashSet<>();
        String cursor = null;
        int pages = 0;
        while (true) {
            Page page = notes.listForViewer(ownerId, cursor, 2);
            for (NoteSummary s : page.items()) {
                assertTrue(seen.add(s.id()), "duplicate note across pages: " + s.id());
            }
            pages++;
            if (page.nextCursor() == null) {
                break;
            }
            cursor = page.nextCursor();
            assertTrue(pages < 10, "pagination did not terminate");
        }
        assertEquals(new HashSet<>(ids), seen);
    }

    @Test
    void listForViewerOnlyIncludesOwnedOrSharedNotes() {
        Note owned = notes.create(ownerId, "Owned", "Body");
        Note notShared = notes.create(otherUserId, "Not shared", "Body");
        Note shared = notes.create(otherUserId, "Shared", "Body");
        notes.upsertShare(shared.id(), ownerId, Permission.READ);

        Page page = notes.listForViewer(ownerId, null, 50);
        Set<String> ids = page.items().stream().map(NoteSummary::id).collect(java.util.stream.Collectors.toSet());
        assertTrue(ids.contains(owned.id()));
        assertTrue(ids.contains(shared.id()));
        assertTrue(!ids.contains(notShared.id()));
    }

    @Test
    void invalidCursorIsRejected() {
        assertThrows(app.nanotes.backend.apperr.ValidationException.class, () -> notes.listForViewer(ownerId, "not-a-cursor", 10));
    }

    @Test
    void getPublicShareIsNullForUnpublishedNote() {
        Note created = notes.create(ownerId, "Title", "Body");
        assertNull(notes.getPublicShare(created.id()).orElse(null));
    }

    /** Timestamps have nanosecond precision but the system clock's real resolution may be coarser on CI. */
    private static void sleepToAdvanceClock() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
