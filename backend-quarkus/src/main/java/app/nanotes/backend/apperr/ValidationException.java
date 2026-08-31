package app.nanotes.backend.apperr;

/** Request failed input validation. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
