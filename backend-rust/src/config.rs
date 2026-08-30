//! Runtime configuration loaded from environment variables. Every value
//! here is shared, by name, with every other backend implementation in
//! this repository (see /README.md) — do not rename without updating the
//! root .env.example and docs.

#[derive(Debug, Clone)]
pub struct Config {
    // HTTP
    pub listen_addr: String,
    pub public_base_url: String,
    pub frontend_url: String,
    pub allowed_origins: Vec<String>,

    // Session / CSRF
    pub session_secret: String,
    pub cookie_domain: Option<String>,

    // OIDC
    pub oidc_issuer_url: String,
    pub oidc_client_id: String,
    pub oidc_client_secret: String,
    pub oidc_redirect_url: String,
    pub oidc_scopes: Vec<String>,

    // Database (SQLite only, see docs/decisions/0002-sqlite-only.md)
    pub database_url: String,

    // SMTP
    pub smtp_host: String,
    pub smtp_port: u16,
    pub smtp_username: String,
    pub smtp_password: String,
    pub smtp_from: String,
}

impl Config {
    pub fn load() -> Result<Config, String> {
        let cfg = Config {
            listen_addr: env_or("LISTEN_ADDR", ":8080"),
            public_base_url: env_or("PUBLIC_BASE_URL", "http://localhost:8080"),
            frontend_url: env_or("FRONTEND_URL", "http://localhost:5173"),
            allowed_origins: split_csv(&env_or("ALLOWED_ORIGINS", "http://localhost:5173")),
            session_secret: std::env::var("SESSION_SECRET").unwrap_or_default(),
            cookie_domain: std::env::var("COOKIE_DOMAIN")
                .ok()
                .filter(|s| !s.is_empty()),
            oidc_issuer_url: std::env::var("OIDC_ISSUER_URL").unwrap_or_default(),
            oidc_client_id: std::env::var("OIDC_CLIENT_ID").unwrap_or_default(),
            oidc_client_secret: std::env::var("OIDC_CLIENT_SECRET").unwrap_or_default(),
            oidc_redirect_url: std::env::var("OIDC_REDIRECT_URL").unwrap_or_default(),
            oidc_scopes: split_csv(&env_or("OIDC_SCOPES", "openid,profile,email")),
            database_url: env_or("DATABASE_URL", "./notes.db"),
            smtp_host: std::env::var("SMTP_HOST").unwrap_or_default(),
            smtp_port: env_or("SMTP_PORT", "25").parse().unwrap_or(25),
            smtp_username: std::env::var("SMTP_USERNAME").unwrap_or_default(),
            smtp_password: std::env::var("SMTP_PASSWORD").unwrap_or_default(),
            smtp_from: env_or("SMTP_FROM", "NA Notes <notes@example.com>"),
        };

        let mut missing = Vec::new();
        if cfg.session_secret.is_empty() {
            missing.push("SESSION_SECRET");
        }
        if cfg.oidc_issuer_url.is_empty() {
            missing.push("OIDC_ISSUER_URL");
        }
        if cfg.oidc_client_id.is_empty() {
            missing.push("OIDC_CLIENT_ID");
        }
        if cfg.oidc_client_secret.is_empty() {
            missing.push("OIDC_CLIENT_SECRET");
        }
        if cfg.oidc_redirect_url.is_empty() {
            missing.push("OIDC_REDIRECT_URL");
        }
        if !missing.is_empty() {
            return Err(format!(
                "missing required environment variables: {}",
                missing.join(", ")
            ));
        }

        Ok(cfg)
    }
}

fn env_or(key: &str, fallback: &str) -> String {
    match std::env::var(key) {
        Ok(v) if !v.is_empty() => v,
        _ => fallback.to_string(),
    }
}

fn split_csv(v: &str) -> Vec<String> {
    v.split(',')
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
        .map(|s| s.to_string())
        .collect()
}
