package app.nanotes.backend.apperr;

/** Not logged in. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() {
        super("login required");
    }
}
