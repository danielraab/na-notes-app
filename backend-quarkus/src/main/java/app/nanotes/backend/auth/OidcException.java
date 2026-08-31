package app.nanotes.backend.auth;

/** The OIDC provider could not be reached, or rejected/mismatched a login attempt. */
public class OidcException extends RuntimeException {
    public OidcException(String message) {
        super(message);
    }

    public OidcException(String message, Throwable cause) {
        super(message, cause);
    }
}
