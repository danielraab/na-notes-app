//! PostgreSQL implementation of [`Backend`], via `tokio-postgres` behind a
//! `deadpool` connection pool.

use std::error::Error;
use std::sync::Arc;

use async_trait::async_trait;
use bytes::BytesMut;
use deadpool_postgres::{Manager, ManagerConfig, Pool, RecyclingMethod};
use tokio_postgres::types::{to_sql_checked, IsNull, ToSql, Type};

use crate::apperr::{AppError, Result};

use super::rebind::to_dollar_placeholders;
use super::value::{Row, Value};
use super::Backend;

/// Unlike SQLite, PostgreSQL is a real client/server database that handles
/// concurrent writers itself, so connections are pooled rather than
/// serialized through one.
const POOL_MAX_SIZE: usize = 16;

pub struct PostgresBackend {
    pool: Pool,
}

impl PostgresBackend {
    pub async fn connect(database_url: &str) -> anyhow::Result<PostgresBackend> {
        let pg_config: tokio_postgres::Config = database_url
            .parse()
            .map_err(|e| anyhow::anyhow!("parse DATABASE_URL as a PostgreSQL DSN: {e}"))?;

        let tls = tls_connector()?;
        let manager = Manager::from_config(
            pg_config,
            tls,
            ManagerConfig {
                recycling_method: RecyclingMethod::Fast,
            },
        );
        let pool = Pool::builder(manager).max_size(POOL_MAX_SIZE).build()?;

        // Fail fast on a bad DSN or unreachable server rather than surfacing
        // it on the first request.
        let _probe = pool
            .get()
            .await
            .map_err(|e| anyhow::anyhow!("connect to PostgreSQL: {e}"))?;

        Ok(PostgresBackend { pool })
    }

    async fn client(&self) -> Result<deadpool_postgres::Client> {
        self.pool
            .get()
            .await
            .map_err(|e| AppError::Internal(format!("get pooled connection: {e}")))
    }
}

/// TLS is configured from webpki's bundled root certificates; whether it is
/// actually used is left to the DSN's `sslmode` (tokio-postgres defaults to
/// `prefer`, and honours `sslmode=disable`/`require` as usual).
///
/// The crypto provider is named explicitly rather than taken from rustls's
/// process-wide default: both `ring` and `aws-lc-rs` end up in this binary's
/// dependency tree (via reqwest and lettre), which leaves rustls with no
/// unambiguous default and makes `ClientConfig::builder()` panic.
fn tls_connector() -> anyhow::Result<tokio_postgres_rustls::MakeRustlsConnect> {
    let roots = rustls::RootCertStore {
        roots: webpki_roots::TLS_SERVER_ROOTS.to_vec(),
    };
    let config = rustls::ClientConfig::builder_with_provider(Arc::new(
        rustls::crypto::ring::default_provider(),
    ))
    .with_safe_default_protocol_versions()
    .map_err(|e| anyhow::anyhow!("configure TLS for PostgreSQL: {e}"))?
    .with_root_certificates(roots)
    .with_no_client_auth();
    Ok(tokio_postgres_rustls::MakeRustlsConnect::new(config))
}

impl ToSql for Value {
    fn to_sql(
        &self,
        ty: &Type,
        out: &mut BytesMut,
    ) -> std::result::Result<IsNull, Box<dyn Error + Sync + Send>> {
        match self {
            Value::Null => Ok(IsNull::Yes),
            Value::Text(s) => s.to_sql(ty, out),
            // The schema stores versions as `INTEGER` (int4 in PostgreSQL)
            // while `LIMIT`/`COUNT` are int8, so narrow to whatever width
            // the server asked for rather than assuming bigint.
            Value::Int(i) => match *ty {
                Type::INT2 => i16::try_from(*i)?.to_sql(ty, out),
                Type::INT4 => i32::try_from(*i)?.to_sql(ty, out),
                _ => i.to_sql(ty, out),
            },
        }
    }

    fn accepts(ty: &Type) -> bool {
        matches!(
            *ty,
            Type::TEXT
                | Type::VARCHAR
                | Type::BPCHAR
                | Type::NAME
                | Type::UNKNOWN
                | Type::INT2
                | Type::INT4
                | Type::INT8
        )
    }

    to_sql_checked!();
}

fn map_row(row: &tokio_postgres::Row) -> Result<Row> {
    let mut values = Vec::with_capacity(row.columns().len());
    for (idx, column) in row.columns().iter().enumerate() {
        let value = match *column.type_() {
            Type::TEXT | Type::VARCHAR | Type::BPCHAR | Type::NAME => {
                opt(row.try_get::<_, Option<String>>(idx))?.map_or(Value::Null, Value::Text)
            }
            Type::INT2 => opt(row.try_get::<_, Option<i16>>(idx))?
                .map_or(Value::Null, |v| Value::Int(i64::from(v))),
            Type::INT4 => opt(row.try_get::<_, Option<i32>>(idx))?
                .map_or(Value::Null, |v| Value::Int(i64::from(v))),
            Type::INT8 => opt(row.try_get::<_, Option<i64>>(idx))?.map_or(Value::Null, Value::Int),
            Type::BOOL => opt(row.try_get::<_, Option<bool>>(idx))?
                .map_or(Value::Null, |v| Value::Int(i64::from(v))),
            ref other => {
                return Err(AppError::Internal(format!(
                    "column {idx} ({}): unsupported PostgreSQL type {other}",
                    column.name()
                )))
            }
        };
        values.push(value);
    }
    Ok(Row::new(values))
}

fn opt<T>(result: std::result::Result<T, tokio_postgres::Error>) -> Result<T> {
    result.map_err(|e| AppError::Internal(format!("read column: {e}")))
}

/// Borrows a parameter list as the trait-object slice `tokio_postgres` takes.
fn as_params(params: &[Value]) -> Vec<&(dyn ToSql + Sync)> {
    params.iter().map(|v| v as &(dyn ToSql + Sync)).collect()
}

#[async_trait]
impl Backend for PostgresBackend {
    async fn execute(&self, sql: &str, params: Vec<Value>) -> Result<u64> {
        let client = self.client().await?;
        client
            .execute(&to_dollar_placeholders(sql), &as_params(&params))
            .await
            .map_err(|e| AppError::Internal(format!("execute: {e}")))
    }

    async fn query_opt(&self, sql: &str, params: Vec<Value>) -> Result<Option<Row>> {
        let client = self.client().await?;
        let row = client
            .query_opt(&to_dollar_placeholders(sql), &as_params(&params))
            .await
            .map_err(|e| AppError::Internal(format!("query: {e}")))?;
        row.as_ref().map(map_row).transpose()
    }

    async fn query_all(&self, sql: &str, params: Vec<Value>) -> Result<Vec<Row>> {
        let client = self.client().await?;
        let rows = client
            .query(&to_dollar_placeholders(sql), &as_params(&params))
            .await
            .map_err(|e| AppError::Internal(format!("query: {e}")))?;
        rows.iter().map(map_row).collect()
    }

    async fn run_migration(&self, name: &str, sql: &str, applied_at: &str) -> Result<()> {
        let mut client = self.client().await?;
        let tx = client
            .transaction()
            .await
            .map_err(|e| AppError::Internal(format!("begin transaction: {e}")))?;
        // `batch_execute` uses the simple query protocol, which (unlike the
        // extended protocol `execute` uses) accepts a whole multi-statement
        // migration file in one call.
        tx.batch_execute(sql)
            .await
            .map_err(|e| AppError::Internal(format!("apply migration {name}: {e}")))?;
        tx.execute(
            "INSERT INTO schema_migrations (name, applied_at) VALUES ($1, $2)",
            &[&name, &applied_at],
        )
        .await
        .map_err(|e| AppError::Internal(format!("record migration {name}: {e}")))?;
        tx.commit()
            .await
            .map_err(|e| AppError::Internal(format!("commit migration {name}: {e}")))
    }
}
