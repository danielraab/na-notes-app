"""CSPRNG token generation for anything security-sensitive (session IDs,
CSRF tokens, public share tokens, OIDC state/PKCE values). The stdlib
`secrets` module is used exclusively — never `random`.
"""

from __future__ import annotations

import secrets


def new(num_bytes: int) -> str:
    """Return a URL-safe base64 string encoding num_bytes of CSPRNG output."""
    return secrets.token_urlsafe(num_bytes)
