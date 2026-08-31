package app.nanotes.backend.db;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Formats/parses the TEXT timestamps stored in every table (ADR 0014 leaves
 * timestamp storage to each implementation). Always exactly 9 fractional
 * digits, unlike Go's trailing-zero-trimmed RFC3339Nano, so lexicographic
 * ordering of the stored strings always agrees with chronological order —
 * required for the cursor-pagination WHERE clause (ADR 0007) to be correct.
 */
public final class Timestamps {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC);

    private Timestamps() {}

    public static String format(Instant instant) {
        return FORMAT.format(instant);
    }

    public static String now() {
        return format(Instant.now());
    }

    public static Instant parse(String s) {
        return Instant.from(FORMAT.parse(s));
    }
}
