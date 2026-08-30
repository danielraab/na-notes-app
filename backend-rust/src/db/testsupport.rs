//! Test-only database fixture.
//!
//! By default each test gets its own temp-file SQLite database. Setting
//! `POSTGRES_TEST_URL` instead points the *same* tests at a real PostgreSQL
//! server — so the repository suite doubles as the conformance test for both
//! [`Backend`](super::Backend) implementations rather than only exercising
//! the default engine.
//!
//! ```bash
//! POSTGRES_TEST_URL=postgres://postgres@localhost:5432/postgres?sslmode=disable cargo test
//! ```
//!
//! The named database is wiped between tests, so point this at a throwaway
//! server, never one holding anything you care about.

use std::sync::LazyLock;

use tokio::sync::{Mutex, MutexGuard};

use super::Db;

/// Env var opting the suite into running against PostgreSQL.
pub const POSTGRES_TEST_URL: &str = "POSTGRES_TEST_URL";

/// PostgreSQL tests share one server and reset it on setup, so they take
/// turns. Held for the lifetime of a [`TestDb`], this serializes them
/// whatever `--test-threads` says, rather than relying on the caller to pass
/// a flag. SQLite tests each get their own file and never touch this.
static POSTGRES_TEST_LOCK: LazyLock<Mutex<()>> = LazyLock::new(|| Mutex::new(()));

pub struct TestDb {
    db: Db,
    /// Kept alive so the SQLite file outlives the test that uses it.
    _tempdir: Option<tempfile::TempDir>,
    /// Kept alive so no other PostgreSQL test runs concurrently.
    _postgres_turn: Option<MutexGuard<'static, ()>>,
}

impl TestDb {
    pub async fn open() -> TestDb {
        match std::env::var(POSTGRES_TEST_URL) {
            Ok(url) if !url.is_empty() => TestDb::open_postgres(&url).await,
            _ => TestDb::open_sqlite().await,
        }
    }

    async fn open_sqlite() -> TestDb {
        let tempdir = tempfile::tempdir().expect("tempdir");
        let path = tempdir.path().join("test.db");
        let db = Db::open(path.to_str().expect("utf-8 temp path"))
            .await
            .expect("open sqlite test database");
        TestDb {
            db,
            _tempdir: Some(tempdir),
            _postgres_turn: None,
        }
    }

    /// Drops and recreates the whole `public` schema, then lets `Db::open`
    /// migrate it back, so each test starts from an empty database.
    async fn open_postgres(url: &str) -> TestDb {
        let turn = POSTGRES_TEST_LOCK.lock().await;

        let admin = Db::open(url).await.expect("connect to PostgreSQL");
        admin
            .execute("DROP SCHEMA IF EXISTS public CASCADE", Vec::new())
            .await
            .expect("drop public schema");
        admin
            .execute("CREATE SCHEMA public", Vec::new())
            .await
            .expect("recreate public schema");
        drop(admin);

        let db = Db::open(url)
            .await
            .expect("migrate PostgreSQL test database");
        TestDb {
            db,
            _tempdir: None,
            _postgres_turn: Some(turn),
        }
    }

    pub fn handle(&self) -> Db {
        self.db.clone()
    }
}
