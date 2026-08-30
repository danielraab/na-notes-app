//! Owns the database connection(s) and forward-only migrations. No other
//! module may open a database connection directly (see
//! /docs/adr/0006-sqlite-owned-by-backend.md and
//! /docs/adr/0013-exchangeable-database-backend.md).
//!
//! Both supported engines sit behind the [`Backend`] trait, so repositories
//! write one set of SQL against one API and never branch on which engine is
//! configured — see docs/decisions/0002-database-abstraction-layer.md.

mod postgres;
mod rebind;
mod sqlite;
#[cfg(test)]
pub mod testsupport;
mod value;

use std::sync::Arc;

use async_trait::async_trait;
use include_dir::{include_dir, Dir};

use crate::apperr::Result;
use crate::timefmt::fmt_time;

pub use value::{Row, Value};

use postgres::PostgresBackend;
use sqlite::SqliteBackend;

static MIGRATIONS_DIR: Dir<'_> = include_dir!("$CARGO_MANIFEST_DIR/src/db/migrations");

/// What a database engine has to provide. SQL reaching these methods is
/// always written in the canonical dialect described in
/// `docs/decisions/0002-database-abstraction-layer.md` (`?N` placeholders,
/// portable syntax); an implementation adapts it to its own driver.
///
/// Adding a third engine means implementing this trait and one arm of
/// [`Engine::from_url`] — no repository changes.
#[async_trait]
pub trait Backend: Send + Sync {
    /// Runs a statement, returning the number of rows it affected.
    async fn execute(&self, sql: &str, params: Vec<Value>) -> Result<u64>;

    /// Runs a query expected to match at most one row.
    async fn query_opt(&self, sql: &str, params: Vec<Value>) -> Result<Option<Row>>;

    /// Runs a query, returning every matching row.
    async fn query_all(&self, sql: &str, params: Vec<Value>) -> Result<Vec<Row>>;

    /// Applies one migration file and records it in `schema_migrations`,
    /// atomically. The engine's own transaction API is used, since that is
    /// the one place a statement group has to be all-or-nothing.
    async fn run_migration(&self, name: &str, sql: &str, applied_at: &str) -> Result<()>;
}

/// The engine a `DATABASE_URL` selects, per ADR 0013.
enum Engine {
    /// A SQLite file at this path — a bare path, `sqlite://<path>`, or
    /// `file:<path>`.
    Sqlite(String),
    /// PostgreSQL, from a `postgres://` / `postgresql://` DSN passed through
    /// to the driver as-is.
    Postgres(String),
}

impl Engine {
    fn from_url(database_url: &str) -> Engine {
        if database_url.starts_with("postgres://") || database_url.starts_with("postgresql://") {
            Engine::Postgres(database_url.to_string())
        } else if let Some(path) = database_url.strip_prefix("sqlite://") {
            Engine::Sqlite(path.to_string())
        } else if let Some(path) = database_url.strip_prefix("file:") {
            Engine::Sqlite(path.to_string())
        } else {
            Engine::Sqlite(database_url.to_string())
        }
    }
}

/// The handle every repository holds. Cloning is cheap and shares the
/// underlying connection/pool.
#[derive(Clone)]
pub struct Db {
    backend: Arc<dyn Backend>,
}

impl Db {
    /// Opens the database identified by `database_url` — its scheme selects
    /// the engine (ADR 0013) — creating it if necessary and applying any
    /// migrations that haven't run yet.
    pub async fn open(database_url: &str) -> anyhow::Result<Db> {
        let backend: Arc<dyn Backend> = match Engine::from_url(database_url) {
            Engine::Sqlite(path) => Arc::new(SqliteBackend::open(&path)?),
            Engine::Postgres(url) => Arc::new(PostgresBackend::connect(&url).await?),
        };
        let db = Db { backend };
        db.migrate().await?;
        Ok(db)
    }

    pub async fn execute(&self, sql: &str, params: Vec<Value>) -> Result<u64> {
        self.backend.execute(sql, params).await
    }

    pub async fn query_opt(&self, sql: &str, params: Vec<Value>) -> Result<Option<Row>> {
        self.backend.query_opt(sql, params).await
    }

    pub async fn query_all(&self, sql: &str, params: Vec<Value>) -> Result<Vec<Row>> {
        self.backend.query_all(sql, params).await
    }

    /// Applies every embedded migration that isn't recorded as applied yet,
    /// in filename order. Engine-agnostic: only the per-file transaction is
    /// delegated to the backend.
    async fn migrate(&self) -> anyhow::Result<()> {
        self.execute(
            "CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY, applied_at TEXT NOT NULL)",
            Vec::new(),
        )
        .await?;

        let mut names: Vec<&str> = MIGRATIONS_DIR
            .files()
            .filter_map(|f| f.path().file_name().and_then(|n| n.to_str()))
            .filter(|n| n.ends_with(".sql"))
            .collect();
        names.sort_unstable();

        for name in names {
            let applied = self
                .query_opt(
                    "SELECT COUNT(*) FROM schema_migrations WHERE name = ?1",
                    crate::params![name],
                )
                .await?
                .map_or(0, |row| row.int(0).unwrap_or(0));
            if applied > 0 {
                continue;
            }

            let sql = MIGRATIONS_DIR
                .get_file(name)
                .and_then(|f| f.contents_utf8())
                .ok_or_else(|| {
                    anyhow::anyhow!("embedded migration {name} is missing or not UTF-8")
                })?;

            self.backend
                .run_migration(name, sql, &fmt_time(chrono::Utc::now()))
                .await?;
        }

        Ok(())
    }
}
