"""Generic OpenID Connect client (ADR 0004): standard Authorization Code
flow with PKCE, configured entirely from the issuer URL and client
credentials — no provider-specific code, so any standards-compliant OIDC
provider works unmodified.

Built on plain `httpx` (discovery + code exchange) and `joserfc` (ID token
signature/claims verification) rather than a higher-level OIDC client
library: the actual protocol surface used here — one GET for discovery,
one GET for JWKS, one POST for the code+PKCE exchange, one JWT decode — is
small enough to keep explicit and easy to audit against ADR 0004/0009,
mirroring how backend-go keeps its own OIDC/PKCE code (internal/auth/oidc.go)
short rather than reaching for a bigger framework.
"""

from __future__ import annotations

import base64
import hashlib
from dataclasses import dataclass
from urllib.parse import urlencode

import httpx
from joserfc import jwt
from joserfc.errors import JoseError
from joserfc.jwk import KeySet

_SUPPORTED_ID_TOKEN_ALGS = ["RS256", "PS256", "ES256"]


class OidcError(RuntimeError):
    pass


@dataclass
class Claims:
    """The subset of ID token / userinfo claims the application needs,
    independent of which OIDC provider issued them."""

    subject: str
    email: str
    display_name: str
    avatar_url: str


def code_challenge(code_verifier: str) -> str:
    """Derives the PKCE S256 code_challenge for a code_verifier."""
    digest = hashlib.sha256(code_verifier.encode("ascii")).digest()
    return base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")


class OidcClient:
    def __init__(
        self,
        *,
        issuer: str,
        authorization_endpoint: str,
        token_endpoint: str,
        jwks_uri: str,
        client_id: str,
        client_secret: str,
        redirect_url: str,
        scopes: list[str],
    ) -> None:
        self._issuer = issuer
        self._authorization_endpoint = authorization_endpoint
        self._token_endpoint = token_endpoint
        self._jwks_uri = jwks_uri
        self._client_id = client_id
        self._client_secret = client_secret
        self._redirect_url = redirect_url
        self._scopes = scopes

    @classmethod
    def discover(
        cls,
        issuer_url: str,
        client_id: str,
        client_secret: str,
        redirect_url: str,
        scopes: list[str],
        timeout: float = 30.0,
    ) -> OidcClient:
        discovery_url = issuer_url.rstrip("/") + "/.well-known/openid-configuration"
        try:
            resp = httpx.get(discovery_url, timeout=timeout)
            resp.raise_for_status()
            metadata = resp.json()
        except httpx.HTTPError as exc:
            raise OidcError(f"discover OIDC provider {issuer_url!r}: {exc}") from exc

        try:
            return cls(
                issuer=metadata["issuer"],
                authorization_endpoint=metadata["authorization_endpoint"],
                token_endpoint=metadata["token_endpoint"],
                jwks_uri=metadata["jwks_uri"],
                client_id=client_id,
                client_secret=client_secret,
                redirect_url=redirect_url,
                scopes=scopes,
            )
        except KeyError as exc:
            raise OidcError(f"OIDC discovery document missing {exc}") from exc

    def auth_code_url(self, state: str, code_verifier: str) -> str:
        params = {
            "response_type": "code",
            "client_id": self._client_id,
            "redirect_uri": self._redirect_url,
            "scope": " ".join(self._scopes),
            "state": state,
            "code_challenge": code_challenge(code_verifier),
            "code_challenge_method": "S256",
        }
        return f"{self._authorization_endpoint}?{urlencode(params)}"

    def _fetch_jwks(self, timeout: float = 10.0) -> KeySet:
        resp = httpx.get(self._jwks_uri, timeout=timeout)
        resp.raise_for_status()
        return KeySet.import_key_set(resp.json())

    def exchange(self, code: str, code_verifier: str) -> Claims:
        """Completes the authorization code flow and verifies the returned
        ID token, returning the caller's identity claims."""
        try:
            resp = httpx.post(
                self._token_endpoint,
                data={
                    "grant_type": "authorization_code",
                    "code": code,
                    "redirect_uri": self._redirect_url,
                    "client_id": self._client_id,
                    "client_secret": self._client_secret,
                    "code_verifier": code_verifier,
                },
                headers={"Accept": "application/json"},
                timeout=30.0,
            )
            resp.raise_for_status()
            token = resp.json()
        except httpx.HTTPError as exc:
            raise OidcError(f"exchange authorization code: {exc}") from exc

        raw_id_token = token.get("id_token")
        if not raw_id_token:
            raise OidcError("token response did not include an id_token")

        try:
            key_set = self._fetch_jwks()
            decoded = jwt.decode(raw_id_token, key_set, algorithms=_SUPPORTED_ID_TOKEN_ALGS)
            claims_registry = jwt.JWTClaimsRegistry(
                iss={"essential": True, "value": self._issuer},
                aud={"essential": True, "value": self._client_id},
            )
            claims_registry.validate(decoded.claims)
        except (JoseError, httpx.HTTPError) as exc:
            raise OidcError(f"verify id_token: {exc}") from exc

        claims = decoded.claims
        email = claims.get("email") or ""
        display_name = claims.get("name") or email
        avatar_url = claims.get("picture") or ""
        return Claims(
            subject=claims["sub"],
            email=email,
            display_name=display_name,
            avatar_url=avatar_url,
        )
