package db

import (
	"context"
	"path/filepath"
	"testing"
)

// Open dispatches on databaseURL's scheme (ADR 0013); all of these forms
// must resolve to a working SQLite database.
func TestOpenSchemeDispatchToSQLite(t *testing.T) {
	for _, prefix := range []string{"", "sqlite://", "file:"} {
		t.Run(prefix, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "test.db")
			d, err := Open(prefix + path)
			if err != nil {
				t.Fatalf("Open(%q): %v", prefix+path, err)
			}
			defer d.Close()

			if d.driver != driverSQLite {
				t.Errorf("driver = %q, want %q", d.driver, driverSQLite)
			}
			// Migrations must have run: the users table should exist and
			// accept a row.
			if _, err := d.ExecContext(context.Background(),
				`INSERT INTO users (id, oidc_subject, email, display_name, created_at) VALUES (?, ?, ?, ?, ?)`,
				"u1", "subj", "u@example.com", "U", "2024-01-01T00:00:00Z",
			); err != nil {
				t.Errorf("insert after Open(%q): %v", prefix+path, err)
			}
		})
	}
}
