package app.nanotes.backend.db

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Formats/parses the TEXT timestamps stored in every table (ADR 0014 leaves
 * timestamp storage to each implementation). Always exactly 9 fractional
 * digits, unlike Go's trailing-zero-trimmed RFC3339Nano, so lexicographic
 * ordering of the stored strings always agrees with chronological order —
 * required for the cursor-pagination WHERE clause (ADR 0007) to be correct.
 */
object Timestamps {
    private val FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC)

    fun format(instant: Instant): String = FORMAT.format(instant)

    fun now(): String = format(Instant.now())

    fun parse(s: String): Instant = Instant.from(FORMAT.parse(s))
}
