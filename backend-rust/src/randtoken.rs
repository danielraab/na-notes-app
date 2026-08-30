//! Cryptographically random, URL-safe tokens for anything security-sensitive
//! (session IDs, CSRF tokens, public share tokens, OIDC state/PKCE values).
//! `rand::rngs::OsRng` draws directly from the OS CSPRNG — never use a
//! non-cryptographic RNG for these.

use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use rand::RngCore;

/// Returns a URL-safe base64 string encoding `n` bytes (n*8 bits) read from
/// a CSPRNG.
pub fn new(n: usize) -> String {
    let mut buf = vec![0u8; n];
    rand::rngs::OsRng.fill_bytes(&mut buf);
    URL_SAFE_NO_PAD.encode(buf)
}
