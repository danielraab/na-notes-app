//! SQLite implementation of [`Backend`], via `rusqlite`.

use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use rusqlite::types::{ToSqlOutput, ValueRef};
use rusqlite::{Connection, ToSql};

use crate::apperr::{AppError, Result};

use super::value::{Row, Value};
use super::Backend;

/// SQLite is single-writer, so there is nothing to gain from pooling
/// connections in-process: one connection behind a mutex, with every call
/// moved onto a blocking thread (`rusqlite` is synchronous and must never
/// block the async runtime), mirrors backend-go's `SetMaxOpenConns(1)`.
pub struct SqliteBackend {
    conn: Arc<Mutex<Connection>>,
}

impl SqliteBackend {
    /// Opens (creating if necessary) the SQLite database file at `path`.
    pub fn open(path: &str) -> anyhow::Result<SqliteBackend> {
        if let Some(parent) = std::path::Path::new(path).parent() {
            if !parent.as_os_str().is_empty() {
                std::fs::create_dir_all(parent)?;
            }
        }

        let conn = Connection::open(path)?;
        // WAL lets readers proceed during a write.
        conn.pragma_update(None, "journal_mode", "WAL")?;
        conn.pragma_update(None, "foreign_keys", "ON")?;
        conn.busy_timeout(std::time::Duration::from_secs(5))?;

        Ok(SqliteBackend {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    /// Runs `f` against the shared connection on a blocking thread.
    async fn call<F, T>(&self, f: F) -> Result<T>
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

impl From<rusqlite::Error> for AppError {
    fn from(err: rusqlite::Error) -> Self {
        if matches!(err, rusqlite::Error::QueryReturnedNoRows) {
            AppError::NotFound
        } else {
            AppError::Internal(err.to_string())
        }
    }
}

impl ToSql for Value {
    fn to_sql(&self) -> rusqlite::Result<ToSqlOutput<'_>> {
        Ok(match self {
            Value::Null => ToSqlOutput::from(rusqlite::types::Null),
            Value::Text(s) => ToSqlOutput::from(s.as_str()),
            Value::Int(i) => ToSqlOutput::from(*i),
        })
    }
}

fn map_row(row: &rusqlite::Row, columns: usize) -> rusqlite::Result<Row> {
    let mut values = Vec::with_capacity(columns);
    for idx in 0..columns {
        values.push(match row.get_ref(idx)? {
            ValueRef::Null => Value::Null,
            ValueRef::Integer(i) => Value::Int(i),
            ValueRef::Text(t) => Value::Text(String::from_utf8_lossy(t).into_owned()),
            ValueRef::Real(_) => {
                return Err(rusqlite::Error::InvalidColumnType(
                    idx,
                    row.as_ref().column_name(idx)?.to_string(),
                    rusqlite::types::Type::Real,
                ))
            }
            ValueRef::Blob(_) => {
                return Err(rusqlite::Error::InvalidColumnType(
                    idx,
                    row.as_ref().column_name(idx)?.to_string(),
                    rusqlite::types::Type::Blob,
                ))
            }
        });
    }
    Ok(Row::new(values))
}

#[async_trait]
impl Backend for SqliteBackend {
    async fn execute(&self, sql: &str, params: Vec<Value>) -> Result<u64> {
        let sql = sql.to_string();
        self.call(move |conn| {
            let affected = conn.execute(&sql, rusqlite::params_from_iter(params.iter()))?;
            Ok(affected as u64)
        })
        .await
    }

    async fn query_opt(&self, sql: &str, params: Vec<Value>) -> Result<Option<Row>> {
        Ok(self.query_all(sql, params).await?.into_iter().next())
    }

    async fn query_all(&self, sql: &str, params: Vec<Value>) -> Result<Vec<Row>> {
        let sql = sql.to_string();
        self.call(move |conn| {
            let mut stmt = conn.prepare(&sql)?;
            let columns = stmt.column_count();
            let rows = stmt.query_map(rusqlite::params_from_iter(params.iter()), |row| {
                map_row(row, columns)
            })?;
            let mut out = Vec::new();
            for row in rows {
                out.push(row?);
            }
            Ok(out)
        })
        .await
    }

    async fn run_migration(&self, name: &str, sql: &str, applied_at: &str) -> Result<()> {
        let (name, sql, applied_at) = (name.to_string(), sql.to_string(), applied_at.to_string());
        self.call(move |conn| {
            let tx = conn.unchecked_transaction()?;
            tx.execute_batch(&sql)?;
            tx.execute(
                "INSERT INTO schema_migrations (name, applied_at) VALUES (?1, ?2)",
                rusqlite::params![name, applied_at],
            )?;
            tx.commit()?;
            Ok(())
        })
        .await
    }
}
