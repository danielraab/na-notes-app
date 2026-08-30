//! Wraps a standards-compliant OpenID Connect provider. Intentionally
//! provider-agnostic (ADR 0004): configuration is limited to the issuer
//! URL and client credentials, nothing provider-specific.

use openidconnect::core::{CoreAuthenticationFlow, CoreClient, CoreProviderMetadata};
use openidconnect::reqwest::async_http_client;
use openidconnect::{
    AuthorizationCode, ClientId, ClientSecret, CsrfToken, IssuerUrl, Nonce, PkceCodeChallenge,
    PkceCodeVerifier, RedirectUrl, Scope, TokenResponse,
};

/// The subset of ID token / userinfo claims the application needs,
/// independent of which OIDC provider issued them.
pub struct Claims {
    pub subject: String,
    pub email: String,
    pub display_name: String,
    pub avatar_url: String,
}

pub struct Oidc {
    client: CoreClient,
    scopes: Vec<String>,
}

impl Oidc {
    pub async fn new(
        issuer_url: &str,
        client_id: &str,
        client_secret: &str,
        redirect_url: &str,
        scopes: Vec<String>,
    ) -> anyhow::Result<Oidc> {
        let metadata = CoreProviderMetadata::discover_async(
            IssuerUrl::new(issuer_url.to_string())?,
            async_http_client,
        )
        .await
        .map_err(|e| anyhow::anyhow!("discover OIDC provider {issuer_url:?}: {e}"))?;

        let client = CoreClient::from_provider_metadata(
            metadata,
            ClientId::new(client_id.to_string()),
            Some(ClientSecret::new(client_secret.to_string())),
        )
        .set_redirect_uri(RedirectUrl::new(redirect_url.to_string())?);

        Ok(Oidc { client, scopes })
    }

    pub fn auth_code_url(&self, state: String, code_verifier: &str) -> String {
        let pkce_verifier = PkceCodeVerifier::new(code_verifier.to_string());
        let pkce_challenge = PkceCodeChallenge::from_code_verifier_sha256(&pkce_verifier);

        let mut request = self
            .client
            .authorize_url(
                CoreAuthenticationFlow::AuthorizationCode,
                move || CsrfToken::new(state.clone()),
                Nonce::new_random,
            )
            .set_pkce_challenge(pkce_challenge);
        // `authorize_url` already adds the mandatory "openid" scope itself;
        // skip it here so a configured `openid,profile,email` doesn't end
        // up duplicated in the resulting URL.
        for scope in self.scopes.iter().filter(|s| s.as_str() != "openid") {
            request = request.add_scope(Scope::new(scope.clone()));
        }
        let (url, _csrf_token, _nonce) = request.url();
        url.to_string()
    }

    /// Completes the authorization code flow and verifies the returned ID
    /// token, returning the caller's identity claims.
    pub async fn exchange(&self, code: String, code_verifier: String) -> anyhow::Result<Claims> {
        let token_response = self
            .client
            .exchange_code(AuthorizationCode::new(code))
            .set_pkce_verifier(PkceCodeVerifier::new(code_verifier))
            .request_async(async_http_client)
            .await
            .map_err(|e| anyhow::anyhow!("exchange authorization code: {e}"))?;

        let id_token = token_response
            .id_token()
            .ok_or_else(|| anyhow::anyhow!("token response did not include an id_token"))?;
        let verifier = self.client.id_token_verifier();
        let claims = id_token
            .claims(&verifier, |_nonce: Option<&Nonce>| Ok::<(), String>(()))
            .map_err(|e| anyhow::anyhow!("verify id_token: {e}"))?;

        let email = claims
            .email()
            .map(|e| e.as_str().to_string())
            .unwrap_or_default();
        let name = claims
            .name()
            .and_then(|n| n.get(None))
            .map(|s| s.as_str().to_string())
            .unwrap_or_default();
        let picture = claims
            .picture()
            .and_then(|p| p.get(None))
            .map(|s| s.as_str().to_string())
            .unwrap_or_default();

        let display_name = if name.is_empty() { email.clone() } else { name };

        Ok(Claims {
            subject: claims.subject().as_str().to_string(),
            email,
            display_name,
            avatar_url: picture,
        })
    }
}
