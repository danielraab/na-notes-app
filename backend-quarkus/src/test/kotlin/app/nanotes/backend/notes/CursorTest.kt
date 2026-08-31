package app.nanotes.backend.notes

import app.nanotes.backend.apperr.ValidationException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class CursorTest {

    @Test
    fun roundTrips() {
        val now = Instant.parse("2024-03-01T12:00:00.123456789Z")
        val encoded = Cursor.encode(now, "note-1")
        val decoded = Cursor.decode(encoded)
        assertEquals("note-1", decoded.id)
        assertEquals("2024-03-01T12:00:00.123456789Z", decoded.updatedAt)
    }

    @Test
    fun rejectsGarbageWithoutASeparator() {
        assertFailsWith<ValidationException> { Cursor.decode("not-a-cursor-at-all") }
    }

    @Test
    fun rejectsInvalidBase64Segments() {
        assertFailsWith<ValidationException> { Cursor.decode("not valid base64.also not valid") }
    }

    @Test
    fun rejectsEmptySegments() {
        assertFailsWith<ValidationException> { Cursor.decode(".") }
    }
}
