package app.nanotes.backend.notes;

import app.nanotes.backend.apperr.ValidationException;
import app.nanotes.backend.db.Timestamps;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;

/**
 * Encodes the stable sort key (updated_at, id) used to page through the
 * notes feed, per ADR 0007. Opaque to clients; only this class constructs
 * or interprets it.
 */
public final class Cursor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public final String updatedAt;
    public final String id;

    private Cursor(String updatedAt, String id) {
        this.updatedAt = updatedAt;
        this.id = id;
    }

    private record Payload(@JsonProperty("u") String u, @JsonProperty("i") String i) {}

    public static String encode(Instant updatedAt, String id) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(new Payload(Timestamps.format(updatedAt), id));
            return ENCODER.encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("failed to encode cursor", e);
        }
    }

    public static Cursor decode(String s) {
        try {
            byte[] bytes = DECODER.decode(s);
            Payload p = MAPPER.readValue(bytes, Payload.class);
            if (p.u() == null || p.u().isEmpty() || p.i() == null || p.i().isEmpty()) {
                throw new ValidationException("invalid cursor");
            }
            return new Cursor(p.u(), p.i());
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("invalid cursor: " + e.getMessage());
        }
    }
}
