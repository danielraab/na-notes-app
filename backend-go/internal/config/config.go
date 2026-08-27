// Package config loads runtime configuration from environment variables.
// Every value here is shared, by name, with every other backend
// implementation in this repository (see /README.md) — do not rename
// without updating the root .env.example and docs.
package config

import (
	"fmt"
	"os"
	"strings"
)

type Config struct {
	// HTTP
	ListenAddr     string
	PublicBaseURL  string
	FrontendURL    string
	AllowedOrigins []string

	// Session / CSRF
	SessionSecret string
	CookieDomain  string

	// OIDC
	OIDCIssuerURL    string
	OIDCClientID     string
	OIDCClientSecret string
	OIDCRedirectURL  string
	OIDCScopes       []string

	// Database
	DatabasePath string

	// SMTP
	SMTPHost     string
	SMTPPort     string
	SMTPUsername string
	SMTPPassword string
	SMTPFrom     string
}

func Load() (*Config, error) {
	cfg := &Config{
		ListenAddr:       getEnv("LISTEN_ADDR", ":8080"),
		PublicBaseURL:    getEnv("PUBLIC_BASE_URL", "http://localhost:8080"),
		FrontendURL:      getEnv("FRONTEND_URL", "http://localhost:5173"),
		AllowedOrigins:   splitCSV(getEnv("ALLOWED_ORIGINS", "http://localhost:5173")),
		SessionSecret:    os.Getenv("SESSION_SECRET"),
		CookieDomain:     os.Getenv("COOKIE_DOMAIN"),
		OIDCIssuerURL:    os.Getenv("OIDC_ISSUER_URL"),
		OIDCClientID:     os.Getenv("OIDC_CLIENT_ID"),
		OIDCClientSecret: os.Getenv("OIDC_CLIENT_SECRET"),
		OIDCRedirectURL:  os.Getenv("OIDC_REDIRECT_URL"),
		OIDCScopes:       splitCSV(getEnv("OIDC_SCOPES", "openid,profile,email")),
		DatabasePath:     getEnv("DATABASE_PATH", "./notes.db"),
		SMTPHost:         os.Getenv("SMTP_HOST"),
		SMTPPort:         getEnv("SMTP_PORT", "25"),
		SMTPUsername:     os.Getenv("SMTP_USERNAME"),
		SMTPPassword:     os.Getenv("SMTP_PASSWORD"),
		SMTPFrom:         getEnv("SMTP_FROM", "NA Notes <notes@example.com>"),
	}

	var missing []string
	if cfg.SessionSecret == "" {
		missing = append(missing, "SESSION_SECRET")
	}
	if cfg.OIDCIssuerURL == "" {
		missing = append(missing, "OIDC_ISSUER_URL")
	}
	if cfg.OIDCClientID == "" {
		missing = append(missing, "OIDC_CLIENT_ID")
	}
	if cfg.OIDCClientSecret == "" {
		missing = append(missing, "OIDC_CLIENT_SECRET")
	}
	if cfg.OIDCRedirectURL == "" {
		missing = append(missing, "OIDC_REDIRECT_URL")
	}
	if len(missing) > 0 {
		return nil, fmt.Errorf("missing required environment variables: %s", strings.Join(missing, ", "))
	}

	return cfg, nil
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func splitCSV(v string) []string {
	parts := strings.Split(v, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			out = append(out, p)
		}
	}
	return out
}
