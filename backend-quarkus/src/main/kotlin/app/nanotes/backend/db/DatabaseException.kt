package app.nanotes.backend.db

/** Wraps a [java.sql.SQLException] as unchecked, since callers can't recover from it. */
class DatabaseException(message: String, cause: Throwable) : RuntimeException(message, cause)
