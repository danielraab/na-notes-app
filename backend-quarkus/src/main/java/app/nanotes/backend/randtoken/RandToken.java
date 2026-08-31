package app.nanotes.backend.randtoken;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographically random, URL-safe tokens for anything security-sensitive
 * (session IDs, CSRF tokens, public share tokens, OIDC state/PKCE values).
 * {@link SecureRandom} must never be swapped for {@link java.util.Random}.
 */
public final class RandToken {

    private static final SecureRandom RNG = new SecureRandom();

    private RandToken() {}

    /** Returns a URL-safe base64 string encoding {@code nBytes} (nBytes*8 bits) read from a CSPRNG. */
    public static String generate(int nBytes) {
        byte[] b = new byte[nBytes];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
