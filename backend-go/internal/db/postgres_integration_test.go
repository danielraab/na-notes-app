package db

import (
	"context"
	"os"
	"testing"
	"time"
)

// TestOpenPostgresAndMigrate is opt-in: it only runs against a real
// PostgreSQL instance, since none is available in most dev/CI sandboxes.
// Set POSTGRES_TEST_URL (e.g. postgres://user:pass@localhost:5432/notes_test)
// to exercise it, e.g.:
//
//	docker run --rm -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16
//	POSTGRES_TEST_URL=postgres://postgres:postgres@localhost:5432/postgres?sslmode=disable go test ./internal/db/...
func TestOpenPostgresAndMigrate(t *testing.T) {
	url := os.Getenv("POSTGRES_TEST_URL")
	if url == "" {
		t.Skip("POSTGRES_TEST_URL not set; skipping PostgreSQL integration test")
	}

	d, err := OpenPostgres(url)
	if err != nil {
		t.Fatalf("OpenPostgres: %v", err)
	}
	t.Cleanup(func() { d.Close() })

	ctx := context.Background()
	subject := "pg-integration-" + time.Now().UTC().Format(time.RFC3339Nano)
	id := subject

	if _, err := d.ExecContext(ctx,
		`INSERT INTO users (id, oidc_subject, email, display_name, avatar_url, created_at) VALUES (?, ?, ?, ?, ?, ?)`,
		id, subject, subject+"@example.com", "PG Integration", nil, time.Now().UTC().Format(time.RFC3339Nano),
	); err != nil {
		t.Fatalf("insert user: %v", err)
	}
	t.Cleanup(func() {
		_, _ = d.ExecContext(context.Background(), `DELETE FROM users WHERE id = ?`, id)
	})

	var displayName string
	if err := d.QueryRowContext(ctx, `SELECT display_name FROM users WHERE id = ?`, id).Scan(&displayName); err != nil {
		t.Fatalf("select user: %v", err)
	}
	if displayName != "PG Integration" {
		t.Errorf("displayName = %q, want %q", displayName, "PG Integration")
	}

	// The `?` placeholder in a note_mentions-style ON CONFLICT DO NOTHING
	// upsert must not error on a duplicate insert.
	if _, err := d.ExecContext(ctx,
		`INSERT INTO users (id, oidc_subject, email, display_name, avatar_url, created_at) VALUES (?, ?, ?, ?, ?, ?)
		 ON CONFLICT (id) DO NOTHING`,
		id, subject, subject+"@example.com", "PG Integration", nil, time.Now().UTC().Format(time.RFC3339Nano),
	); err != nil {
		t.Errorf("duplicate insert with ON CONFLICT DO NOTHING: %v", err)
	}

	// ON CONFLICT ... DO UPDATE SET x = excluded.x, as used by
	// notes.Repository.UpsertShare/CreatePublicShare.
	if _, err := d.ExecContext(ctx,
		`INSERT INTO users (id, oidc_subject, email, display_name, avatar_url, created_at) VALUES (?, ?, ?, ?, ?, ?)
		 ON CONFLICT (id) DO UPDATE SET display_name = excluded.display_name`,
		id, subject, subject+"@example.com", "PG Integration Updated", nil, time.Now().UTC().Format(time.RFC3339Nano),
	); err != nil {
		t.Fatalf("ON CONFLICT DO UPDATE: %v", err)
	}
	if err := d.QueryRowContext(ctx, `SELECT display_name FROM users WHERE id = ?`, id).Scan(&displayName); err != nil {
		t.Fatalf("select updated user: %v", err)
	}
	if displayName != "PG Integration Updated" {
		t.Errorf("displayName after upsert = %q, want %q", displayName, "PG Integration Updated")
	}

	// Row-value comparison, as used by notes.Repository.ListForViewer's
	// cursor pagination (`WHERE (a, b) < (?, ?)`).
	var count int
	if err := d.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM users WHERE (created_at, id) < (?, ?)`,
		"9999-01-01T00:00:00Z", "~",
	).Scan(&count); err != nil {
		t.Errorf("row-value comparison query: %v", err)
	}

	// RowsAffected on a conditional UPDATE, as used by
	// notes.Repository.Update's optimistic-concurrency check.
	res, err := d.ExecContext(ctx, `UPDATE users SET display_name = ? WHERE id = ? AND display_name = ?`,
		"PG Integration Final", id, "PG Integration Updated")
	if err != nil {
		t.Fatalf("conditional update: %v", err)
	}
	if affected, _ := res.RowsAffected(); affected != 1 {
		t.Errorf("RowsAffected = %d, want 1", affected)
	}
}
