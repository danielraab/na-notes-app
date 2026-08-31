package app.nanotes.backend.db;

/** Wraps a {@link java.sql.SQLException} as unchecked, since callers can't recover from it. */
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
