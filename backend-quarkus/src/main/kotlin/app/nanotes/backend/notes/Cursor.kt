package app.nanotes.backend.notes

import app.nanotes.backend.apperr.ValidationException
import app.nanotes.backend.db.Timestamps
import java.time.Instant
import java.util.Base64

/**
 * Encodes the stable sort key (updated_at, id) used to page through the
 * notes feed, per ADR 0007. Opaque to clients; only this class constructs
 * or interprets it. Each field is base64url-encoded separately and joined
 * with `.` (itself URL-safe), so the whole cursor is a plain URL-safe
 * string with no extra encoding layer needed.
 */
class Cursor private constructor(val updatedAt: String, val id: String) {

    companion object {
        private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val DECODER: Base64.Decoder = Base64.getUrlDecoder()

        fun encode(updatedAt: Instant, id: String): String {
            val u = ENCODER.encodeToString(Timestamps.format(updatedAt).toByteArray(Charsets.UTF_8))
            val i = ENCODER.encodeToString(id.toByteArray(Charsets.UTF_8))
            return "$u.$i"
        }

        fun decode(s: String): Cursor {
            val parts = s.split(".", limit = 2)
            if (parts.size != 2) {
                throw ValidationException("invalid cursor")
            }
            return try {
                val u = String(DECODER.decode(parts[0]), Charsets.UTF_8)
                val i = String(DECODER.decode(parts[1]), Charsets.UTF_8)
                if (u.isEmpty() || i.isEmpty()) throw ValidationException("invalid cursor")
                Cursor(u, i)
            } catch (e: ValidationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw ValidationException("invalid cursor: ${e.message}")
            }
        }
    }
}
