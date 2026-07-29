package users

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/danielraab/na-notes-app/backend-go/internal/db"
)

func newTestRepo(t *testing.T) *Repository {
	t.Helper()
	sqlDB, err := db.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	t.Cleanup(func() { sqlDB.Close() })
	return NewRepository(sqlDB)
}

// Regression test: created_at is stored as TEXT and must be parsed back
// into time.Time by hand — modernc.org/sqlite returns TEXT columns as
// strings, so Scanning directly into a *time.Time field compiles but
// fails at run time on every read.
func TestUpsertAndGetByIDRoundTripCreatedAt(t *testing.T) {
	ctx := context.Background()
	repo := newTestRepo(t)

	created, err := repo.UpsertFromOIDC(ctx, "subject-1", "alice@example.com", "Alice", "")
	if err != nil {
		t.Fatalf("UpsertFromOIDC (create): %v", err)
	}
	if created.CreatedAt.IsZero() {
		t.Error("CreatedAt was not set on creation")
	}

	fetched, err := repo.GetByID(ctx, created.ID)
	if err != nil {
		t.Fatalf("GetByID: %v", err)
	}
	if fetched.ID != created.ID || fetched.Email != "alice@example.com" {
		t.Errorf("fetched user mismatch: %+v", fetched)
	}
	if !fetched.CreatedAt.Equal(created.CreatedAt) {
		t.Errorf("CreatedAt = %v, want %v", fetched.CreatedAt, created.CreatedAt)
	}

	// Second login with the same subject updates the profile rather than
	// creating a second account, and must still round-trip CreatedAt.
	updated, err := repo.UpsertFromOIDC(ctx, "subject-1", "alice2@example.com", "Alice Updated", "")
	if err != nil {
		t.Fatalf("UpsertFromOIDC (update): %v", err)
	}
	if updated.ID != created.ID {
		t.Errorf("second login with same subject created a new user: %s != %s", updated.ID, created.ID)
	}
	if updated.Email != "alice2@example.com" || updated.DisplayName != "Alice Updated" {
		t.Errorf("profile was not refreshed: %+v", updated)
	}
	if !updated.CreatedAt.Equal(created.CreatedAt) {
		t.Errorf("CreatedAt changed on update: %v != %v", updated.CreatedAt, created.CreatedAt)
	}
}

func TestSearchExcludesCaller(t *testing.T) {
	ctx := context.Background()
	repo := newTestRepo(t)

	me, err := repo.UpsertFromOIDC(ctx, "me", "me@example.com", "Me", "")
	if err != nil {
		t.Fatalf("create me: %v", err)
	}
	if _, err := repo.UpsertFromOIDC(ctx, "alice", "alice@example.com", "Alice", ""); err != nil {
		t.Fatalf("create alice: %v", err)
	}

	results, err := repo.Search(ctx, me.ID, "Al", 10)
	if err != nil {
		t.Fatalf("Search: %v", err)
	}
	if len(results) != 1 || results[0].DisplayName != "Alice" {
		t.Errorf("Search results = %+v, want just Alice", results)
	}

	results, err = repo.Search(ctx, me.ID, "Me", 10)
	if err != nil {
		t.Fatalf("Search: %v", err)
	}
	if len(results) != 0 {
		t.Errorf("Search should exclude the caller, got %+v", results)
	}
}
