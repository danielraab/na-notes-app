package app.nanotes.backend.notes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.nanotes.backend.apperr.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CursorTest {

    @Test
    void roundTrips() {
        Instant now = Instant.parse("2024-03-01T12:00:00.123456789Z");
        String encoded = Cursor.encode(now, "note-1");
        Cursor decoded = Cursor.decode(encoded);
        assertEquals("note-1", decoded.id);
        assertEquals("2024-03-01T12:00:00.123456789Z", decoded.updatedAt);
    }

    @Test
    void rejectsGarbage() {
        assertThrows(ValidationException.class, () -> Cursor.decode("not-valid-base64!!"));
    }

    @Test
    void rejectsEmptyPayload() {
        // base64url of `{}` — well-formed JSON, but missing required fields
        assertThrows(ValidationException.class, () -> Cursor.decode("e30"));
    }
}
