// Package db owns the database connection(s) and forward-only migrations.
// No other package may open a database connection directly (see
// /docs/adr/0006-sqlite-owned-by-backend.md and
// /docs/adr/0013-exchangeable-database-backend.md).
package db

import (
	"context"
	"database/sql"
	"embed"
	"fmt"
	"path"
	"sort"
	"strings"
	"time"

	_ "github.com/jackc/pgx/v5/stdlib"
	_ "modernc.org/sqlite"
)

const (
	driverSQLite   = "sqlite"
	driverPostgres = "postgres"
)

//go:embed migrations/*.sql
var migrationsFS embed.FS

// DB wraps *sql.DB to transparently rebind the `?`-style placeholders used
// throughout this codebase's SQL to whatever the underlying driver expects.
// Every other package continues to use plain database/sql query/exec calls
// unaware of which engine is actually configured.
type DB struct {
	*sql.DB
	driver string
}

func (d *DB) ExecContext(ctx context.Context, query string, args ...any) (sql.Result, error) {
	return d.DB.ExecContext(ctx, rebind(d.driver, query), args...)
}

func (d *DB) QueryContext(ctx context.Context, query string, args ...any) (*sql.Rows, error) {
	return d.DB.QueryContext(ctx, rebind(d.driver, query), args...)
}

func (d *DB) QueryRowContext(ctx context.Context, query string, args ...any) *sql.Row {
	return d.DB.QueryRowContext(ctx, rebind(d.driver, query), args...)
}

// Open opens (creating if necessary) the SQLite database at path and
// applies any migrations that haven't run yet.
func Open(path string) (*DB, error) {
	dsn := fmt.Sprintf("file:%s?_pragma=busy_timeout(5000)&_pragma=foreign_keys(1)", path)
	sqlDB, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}
	// SQLite is single-writer; WAL lets readers proceed during a write.
	if _, err := sqlDB.Exec("PRAGMA journal_mode=WAL"); err != nil {
		return nil, fmt.Errorf("enable WAL: %w", err)
	}
	sqlDB.SetMaxOpenConns(1) // avoid SQLITE_BUSY under concurrent writers

	if err := migrate(sqlDB, driverSQLite); err != nil {
		return nil, fmt.Errorf("migrate: %w", err)
	}
	return &DB{DB: sqlDB, driver: driverSQLite}, nil
}

// OpenPostgres opens a PostgreSQL database identified by databaseURL (a
// postgres://user:pass@host:port/dbname DSN) and applies any migrations
// that haven't run yet. See ADR 0013 for when a backend should offer this
// instead of (or alongside) Open.
func OpenPostgres(databaseURL string) (*DB, error) {
	sqlDB, err := sql.Open("pgx", databaseURL)
	if err != nil {
		return nil, fmt.Errorf("open postgres: %w", err)
	}
	// Fail fast on a bad DSN or unreachable server rather than surfacing it
	// on the first request.
	if err := sqlDB.Ping(); err != nil {
		return nil, fmt.Errorf("ping postgres: %w", err)
	}
	// Unlike SQLite, PostgreSQL is a real client/server database that
	// handles concurrent writers itself, so no MaxOpenConns(1) restriction.

	if err := migrate(sqlDB, driverPostgres); err != nil {
		return nil, fmt.Errorf("migrate: %w", err)
	}
	return &DB{DB: sqlDB, driver: driverPostgres}, nil
}

func migrate(sqlDB *sql.DB, driver string) error {
	createTable := rebind(driver, `CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY, applied_at TEXT NOT NULL)`)
	if _, err := sqlDB.Exec(createTable); err != nil {
		return err
	}

	entries, err := migrationsFS.ReadDir("migrations")
	if err != nil {
		return err
	}
	names := make([]string, 0, len(entries))
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".sql") {
			names = append(names, e.Name())
		}
	}
	sort.Strings(names)

	countQuery := rebind(driver, `SELECT COUNT(*) FROM schema_migrations WHERE name = ?`)
	insertQuery := rebind(driver, `INSERT INTO schema_migrations (name, applied_at) VALUES (?, ?)`)

	for _, name := range names {
		var applied int
		if err := sqlDB.QueryRow(countQuery, name).Scan(&applied); err != nil {
			return err
		}
		if applied > 0 {
			continue
		}

		sqlBytes, err := migrationsFS.ReadFile(path.Join("migrations", name))
		if err != nil {
			return err
		}

		tx, err := sqlDB.Begin()
		if err != nil {
			return err
		}
		// Executed statement-by-statement rather than as one multi-command
		// Exec: PostgreSQL's default query protocol (unlike the SQLite
		// driver's) rejects multiple commands in a single Exec call.
		for _, stmt := range splitStatements(string(sqlBytes)) {
			if _, err := tx.Exec(stmt); err != nil {
				tx.Rollback()
				return fmt.Errorf("apply migration %s: %w", name, err)
			}
		}
		if _, err := tx.Exec(insertQuery, name, time.Now().UTC().Format(time.RFC3339Nano)); err != nil {
			tx.Rollback()
			return err
		}
		if err := tx.Commit(); err != nil {
			return err
		}
	}
	return nil
}

// splitStatements splits a migration file's SQL text into individual
// statements on top-level `;` boundaries (i.e. not inside a string
// literal). Migration files in this codebase don't use dollar-quoting or
// put `;` inside identifiers, so this simple split is sufficient.
func splitStatements(sqlText string) []string {
	var stmts []string
	var cur strings.Builder
	inString := false
	for i := 0; i < len(sqlText); i++ {
		c := sqlText[i]
		switch {
		case c == '\'':
			inString = !inString
			cur.WriteByte(c)
		case c == ';' && !inString:
			if s := strings.TrimSpace(cur.String()); s != "" {
				stmts = append(stmts, s)
			}
			cur.Reset()
		default:
			cur.WriteByte(c)
		}
	}
	if s := strings.TrimSpace(cur.String()); s != "" {
		stmts = append(stmts, s)
	}
	return stmts
}
