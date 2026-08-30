//! Entrypoint: wires config, db, services, and the HTTP server for the
//! na-notes-app Rust backend.

mod apperr;
mod auth;
mod config;
mod db;
mod httpapi;
mod mail;
mod notes;
mod randtoken;
mod timefmt;
mod users;

use std::sync::Arc;

use config::Config;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()),
        )
        .init();

    if let Err(err) = run().await {
        tracing::error!(error = %err, "server exited");
        std::process::exit(1);
    }
}

async fn run() -> anyhow::Result<()> {
    let cfg = Config::load().map_err(|e| anyhow::anyhow!(e))?;

    let sql_db = db::Db::open(&cfg.database_url).await?;

    let oidc = auth::Oidc::new(
        &cfg.oidc_issuer_url,
        &cfg.oidc_client_id,
        &cfg.oidc_client_secret,
        &cfg.oidc_redirect_url,
        cfg.oidc_scopes.clone(),
    )
    .await?;

    let users_repo = users::Repository::new(sql_db.clone());
    let notes_repo = notes::Repository::new(sql_db.clone());
    let mailer = mail::Mailer::new(
        cfg.smtp_host.clone(),
        cfg.smtp_port,
        cfg.smtp_username.clone(),
        cfg.smtp_password.clone(),
        cfg.smtp_from.clone(),
    );
    let notes_service = notes::Service::new(
        notes_repo,
        users_repo.clone(),
        mailer,
        cfg.frontend_url.clone(),
    );
    let auth_store = auth::Store::new(sql_db);

    let listen_addr = normalize_listen_addr(&cfg.listen_addr);

    let deps = Arc::new(httpapi::Deps {
        config: cfg,
        auth_store,
        oidc,
        users: users_repo,
        notes: notes_service,
    });

    let router = httpapi::build_router(deps);

    let listener = tokio::net::TcpListener::bind(&listen_addr).await?;
    tracing::info!(addr = %listen_addr, "listening");
    axum::serve(listener, router).await?;

    Ok(())
}

/// `LISTEN_ADDR` follows Go's `net.Listen` convention where a leading `:`
/// (e.g. `:8080`) means "all interfaces" — Rust's socket APIs need an
/// explicit host instead.
fn normalize_listen_addr(addr: &str) -> String {
    if let Some(port) = addr.strip_prefix(':') {
        format!("0.0.0.0:{port}")
    } else {
        addr.to_string()
    }
}
