package auth

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"fmt"

	"github.com/coreos/go-oidc/v3/oidc"
	"golang.org/x/oauth2"
)

// Claims is the subset of ID token / userinfo claims the application
// needs, independent of which OIDC provider issued them.
type Claims struct {
	Subject     string
	Email       string
	DisplayName string
	AvatarURL   string
}

// OIDC wraps a standards-compliant OpenID Connect provider. It is
// intentionally provider-agnostic (ADR 0004): configuration is limited to
// the issuer URL and client credentials, nothing provider-specific.
type OIDC struct {
	verifier     *oidc.IDTokenVerifier
	oauth2Config oauth2.Config
}

func NewOIDC(ctx context.Context, issuerURL, clientID, clientSecret, redirectURL string, scopes []string) (*OIDC, error) {
	provider, err := oidc.NewProvider(ctx, issuerURL)
	if err != nil {
		return nil, fmt.Errorf("discover OIDC provider %q: %w", issuerURL, err)
	}
	return &OIDC{
		verifier: provider.Verifier(&oidc.Config{ClientID: clientID}),
		oauth2Config: oauth2.Config{
			ClientID:     clientID,
			ClientSecret: clientSecret,
			RedirectURL:  redirectURL,
			Endpoint:     provider.Endpoint(),
			Scopes:       scopes,
		},
	}, nil
}

// CodeChallenge derives the PKCE S256 code_challenge for a code_verifier.
func CodeChallenge(codeVerifier string) string {
	sum := sha256.Sum256([]byte(codeVerifier))
	return base64.RawURLEncoding.EncodeToString(sum[:])
}

func (o *OIDC) AuthCodeURL(state, codeVerifier string) string {
	return o.oauth2Config.AuthCodeURL(state,
		oauth2.SetAuthURLParam("code_challenge", CodeChallenge(codeVerifier)),
		oauth2.SetAuthURLParam("code_challenge_method", "S256"),
	)
}

// Exchange completes the authorization code flow and verifies the
// returned ID token, returning the caller's identity claims.
func (o *OIDC) Exchange(ctx context.Context, code, codeVerifier string) (Claims, error) {
	token, err := o.oauth2Config.Exchange(ctx, code, oauth2.SetAuthURLParam("code_verifier", codeVerifier))
	if err != nil {
		return Claims{}, fmt.Errorf("exchange authorization code: %w", err)
	}

	rawIDToken, ok := token.Extra("id_token").(string)
	if !ok {
		return Claims{}, fmt.Errorf("token response did not include an id_token")
	}
	idToken, err := o.verifier.Verify(ctx, rawIDToken)
	if err != nil {
		return Claims{}, fmt.Errorf("verify id_token: %w", err)
	}

	var c struct {
		Email   string `json:"email"`
		Name    string `json:"name"`
		Picture string `json:"picture"`
	}
	if err := idToken.Claims(&c); err != nil {
		return Claims{}, fmt.Errorf("parse id_token claims: %w", err)
	}

	displayName := c.Name
	if displayName == "" {
		displayName = c.Email
	}
	return Claims{
		Subject:     idToken.Subject,
		Email:       c.Email,
		DisplayName: displayName,
		AvatarURL:   c.Picture,
	}, nil
}
