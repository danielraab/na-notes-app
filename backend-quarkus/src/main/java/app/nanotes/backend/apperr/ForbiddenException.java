package app.nanotes.backend.apperr;

/** Logged in, but not permitted to perform this action. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException() {
        super("forbidden");
    }
}
