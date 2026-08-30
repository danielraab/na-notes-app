//! Owns the SQLite connection and forward-only migrations. No other module
//! may open a database connection directly (see
//! /docs/adr/0006-sqlite-owned-by-backend.md).
//!
//! This implementation only supports SQLite — see
//! docs/decisions/0002-sqlite-only.md for why that's a conformant subset of
//! /docs/adr/0013-exchangeable-database-backend.md rather than a gap.

use std::sync::{Arc, Mutex};

use include_dir::{include_dir, Dir};
use rusqlite::Connection;

use crate::apperr::{AppError, Result};

static MIGRATIONS_DIR: Dir<'_> = include_dir!("$CARGO_MANIFEST_DIR/src/db/migrations");

/// Shared handle to the single SQLite connection. SQLite is single-writer,
/// so (mirroring backend-go's `SetMaxOpenConns(1)`) every request serializes
/// through one connection guarded by a mutex rather than pooling several.
#[derive(Clone)]
pub struct Db {
    conn: Arc<Mutex<Connection>>,
}

impl Db {
    /// Opens (creating if necessary) the SQLite database identified by
    /// `database_url` and applies any migrations that haven't run yet.
    ///
    /// Accepts a bare path (e.g. `./notes.db`), `sqlite://<path>`, or
    /// `file:<path>` — all open a SQLite file at `<path>`, matching the
    /// SQLite forms of ADR 0013's `DATABASE_URL` contract.
    pub fn open(database_url: &str) -> anyhow::Result<Db> {
        if database_url.starts_with("postgres://") || database_url.starts_with("postgresql://") {
            anyhow::bail!(
                "DATABASE_URL {database_url:?} requests PostgreSQL, which this implementation \
                 does not support — see docs/decisions/0002-sqlite-only.md"
            );
        }
        let path = database_url
            .strip_prefix("sqlite://")
            .or_else(|| database_url.strip_prefix("file:"))
            .unwrap_or(database_url);

        if let Some(parent) = std::path::Path::new(path).parent() {
            if !parent.as_os_str().is_empty() {
                std::fs::create_dir_all(parent)?;
            }
        }

        let conn = Connection::open(path)?;
        conn.pragma_update(None, "journal_mode", "WAL")?;
        conn.pragma_update(None, "foreign_keys", "ON")?;
        conn.busy_timeout(std::time::Duration::from_secs(5))?;

        migrate(&conn)?;

        Ok(Db {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    /// Runs `f` against the shared connection on a blocking thread, since
    /// `rusqlite` is synchronous and must never block the async runtime.
    pub async fn call<F, T>(&self, f: F) -> Result<T>
    where
        F: FnOnce(&Connection) -> Result<T> + Send + 'static,
        T: Send + 'static,
    {
        let conn = self.conn.clone();
        tokio::task::spawn_blocking(move || {
            let conn = conn.lock().expect("db connection mutex poisoned");
            f(&conn)
        })
        .await
        .map_err(|e| AppError::Internal(format!("db task panicked: {e}")))?
    }
}

fn migrate(conn: &Connection) -> anyhow::Result<()> {
    conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY, applied_at TEXT NOT NULL)",
    )?;

    let mut names: Vec<&str> = MIGRATIONS_DIR
        .files()
        .filter_map(|f| f.path().file_name().and_then(|n| n.to_str()))
        .filter(|n| n.ends_with(".sql"))
        .collect();
    names.sort();

    for name in names {
        let already: i64 = conn.query_row(
            "SELECT COUNT(*) FROM schema_migrations WHERE name = ?1",
            [name],
            |row| row.get(0),
        )?;
        if already > 0 {
            continue;
        }

        let file = MIGRATIONS_DIR
            .get_file(name)
            .ok_or_else(|| anyhow::anyhow!("embedded migration {name} vanished"))?;
        let sql = file
            .contents_utf8()
            .ok_or_else(|| anyhow::anyhow!("migration {name} is not valid UTF-8"))?;

        let tx = conn.unchecked_transaction()?;
        tx.execute_batch(sql)?;
        tx.execute(
            "INSERT INTO schema_migrations (name, applied_at) VALUES (?1, ?2)",
            rusqlite::params![name, crate::timefmt::fmt_time(chrono::Utc::now())],
        )?;
        tx.commit()?;
    }

    Ok(())
}
