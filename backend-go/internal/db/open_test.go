package db

import (
	"context"
	"os"
	"path/filepath"
	"testing"
)

// Open dispatches on databaseURL's scheme (ADR 0013); all of these forms
// must resolve to a working SQLite database.
func TestOpenSchemeDispatchToSQLite(t *testing.T) {
	cases := []struct {
		name   string
		prefix string
	}{
		{"bare path", ""},
		{"sqlite:// scheme", "sqlite://"},
		{"file: scheme", "file:"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "test.db")
			d, err := Open(tc.prefix + path)
			if err != nil {
				t.Fatalf("Open(%q): %v", tc.prefix+path, err)
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
				t.Errorf("insert after Open(%q): %v", tc.prefix+path, err)
			}
		})
	}
}

// Regression test: a SQLite path containing '#' (e.g. from Go's own
// t.TempDir(), which embeds "#NN" for an unnamed subtest) must not get
// silently truncated at a URI fragment boundary — that would open a
// different, shorter path than requested, e.g. two logically distinct
// databases silently sharing one file on disk.
func TestOpenSQLitePathWithSpecialCharacters(t *testing.T) {
	for _, name := range []string{"has#hash.db", "has?question.db", "has%percent.db"} {
		t.Run(name, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), name)
			d, err := Open(path)
			if err != nil {
				t.Fatalf("Open(%q): %v", path, err)
			}
			defer d.Close()

			if _, err := d.ExecContext(context.Background(),
				`INSERT INTO users (id, oidc_subject, email, display_name, created_at) VALUES (?, ?, ?, ?, ?)`,
				"u1", "subj", "u@example.com", "U", "2024-01-01T00:00:00Z",
			); err != nil {
				t.Fatalf("insert: %v", err)
			}

			if _, err := os.Stat(path); err != nil {
				t.Errorf("expected database file at %q, got: %v", path, err)
			}
		})
	}
}
