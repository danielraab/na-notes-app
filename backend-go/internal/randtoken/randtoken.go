// Package randtoken generates cryptographically random, URL-safe tokens
// for anything security-sensitive (session IDs, CSRF tokens, public share
// tokens, OIDC state/PKCE values). math/rand must never be used for these.
package randtoken

import (
	"crypto/rand"
	"encoding/base64"
)

// New returns a URL-safe base64 string encoding n bytes (n*8 bits) read
// from a CSPRNG.
func New(n int) (string, error) {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}
