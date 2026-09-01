package app.nanotes.backend.randtoken

import java.security.SecureRandom
import java.util.Base64

/**
 * Cryptographically random, URL-safe tokens for anything security-sensitive
 * (session IDs, CSRF tokens, public share tokens, OIDC state/PKCE values).
 * [SecureRandom] must never be swapped for [kotlin.random.Random].
 */
object RandToken {
    // Constructed lazily on first use, not in the object initializer: under
    // GraalVM native-image (Dockerfile.native) Quarkus initializes this class
    // at build time, and a SecureRandom created then would be frozen into the
    // image heap with a build-time seed — native-image rejects that outright.
    private val RNG by lazy { SecureRandom() }

    /** Returns a URL-safe base64 string encoding [nBytes] (nBytes*8 bits) read from a CSPRNG. */
    fun generate(nBytes: Int): String {
        val b = ByteArray(nBytes)
        RNG.nextBytes(b)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }
}
