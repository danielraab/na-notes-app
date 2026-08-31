package app.nanotes.backend.apperr;

/** Resource does not exist, or is not visible to the caller. */
public class NotFoundException extends RuntimeException {
    public NotFoundException() {
        super("not found");
    }

    public NotFoundException(String message) {
        super(message);
    }
}
