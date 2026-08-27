package notes

import (
	"context"
	"errors"
	"path/filepath"
	"testing"
	"time"

	"github.com/danielraab/na-notes-app/backend-go/internal/apperr"
	"github.com/danielraab/na-notes-app/backend-go/internal/db"
	"github.com/danielraab/na-notes-app/backend-go/internal/users"
)

func newTestRepo(t *testing.T) (*Repository, *users.Repository) {
	t.Helper()
	sqlDB, err := db.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	t.Cleanup(func() { sqlDB.Close() })
	return NewRepository(sqlDB), users.NewRepository(sqlDB)
}

func mustCreateUser(t *testing.T, repo *users.Repository, subject string) users.User {
	t.Helper()
	u, err := repo.UpsertFromOIDC(context.Background(), subject, subject+"@example.com", "User "+subject, "")
	if err != nil {
		t.Fatalf("create user: %v", err)
	}
	return u
}

func TestNoteLifecycle(t *testing.T) {
	ctx := context.Background()
	repo, userRepo := newTestRepo(t)
	owner := mustCreateUser(t, userRepo, "owner")

	n, err := repo.Create(ctx, owner.ID, "Title", "Some **content**")
	if err != nil {
		t.Fatalf("Create: %v", err)
	}
	if n.Version != 1 {
		t.Errorf("Version = %d, want 1", n.Version)
	}

	fetched, err := repo.GetByID(ctx, n.ID)
	if err != nil {
		t.Fatalf("GetByID: %v", err)
	}
	if fetched.Title != "Title" || fetched.OwnerID != owner.ID {
		t.Errorf("fetched note mismatch: %+v", fetched)
	}

	updated, err := repo.Update(ctx, n.ID, "New title", "New content", n.Version)
	if err != nil {
		t.Fatalf("Update: %v", err)
	}
	if updated.Version != 2 || updated.Title != "New title" {
		t.Errorf("unexpected update result: %+v", updated)
	}

	if _, err := repo.Update(ctx, n.ID, "Stale write", "x", 1); !errors.Is(err, apperr.ErrVersionConflict) {
		t.Errorf("Update with stale version: err = %v, want ErrVersionConflict", err)
	}

	if err := repo.Delete(ctx, n.ID); err != nil {
		t.Fatalf("Delete: %v", err)
	}
	if _, err := repo.GetByID(ctx, n.ID); !errors.Is(err, apperr.ErrNotFound) {
		t.Errorf("GetByID after delete: err = %v, want ErrNotFound", err)
	}
}

func TestSharingVisibility(t *testing.T) {
	ctx := context.Background()
	repo, userRepo := newTestRepo(t)
	owner := mustCreateUser(t, userRepo, "owner")
	other := mustCreateUser(t, userRepo, "other")

	n, err := repo.Create(ctx, owner.ID, "Private", "content")
	if err != nil {
		t.Fatalf("Create: %v", err)
	}

	if _, ok, err := repo.SharePermission(ctx, n.ID, other.ID); err != nil || ok {
		t.Fatalf("expected no share before sharing: ok=%v err=%v", ok, err)
	}

	page, err := repo.ListForViewer(ctx, other.ID, "", 12)
	if err != nil {
		t.Fatalf("ListForViewer: %v", err)
	}
	if len(page.Items) != 0 {
		t.Errorf("expected note to be invisible to non-shared user, got %d items", len(page.Items))
	}

	if err := repo.UpsertShare(ctx, n.ID, other.ID, PermissionRead); err != nil {
		t.Fatalf("UpsertShare: %v", err)
	}

	perm, ok, err := repo.SharePermission(ctx, n.ID, other.ID)
	if err != nil || !ok || perm != PermissionRead {
		t.Fatalf("SharePermission after share: perm=%v ok=%v err=%v", perm, ok, err)
	}

	page, err = repo.ListForViewer(ctx, other.ID, "", 12)
	if err != nil {
		t.Fatalf("ListForViewer after share: %v", err)
	}
	if len(page.Items) != 1 || page.Items[0].MyPermission != PermissionRead {
		t.Errorf("expected shared note visible with read permission, got %+v", page.Items)
	}

	if err := repo.DeleteShare(ctx, n.ID, other.ID); err != nil {
		t.Fatalf("DeleteShare: %v", err)
	}
	if _, ok, _ := repo.SharePermission(ctx, n.ID, other.ID); ok {
		t.Error("expected share to be gone after DeleteShare")
	}
}

func TestPublicShareUsesUnguessableToken(t *testing.T) {
	ctx := context.Background()
	repo, userRepo := newTestRepo(t)
	owner := mustCreateUser(t, userRepo, "owner")

	n, err := repo.Create(ctx, owner.ID, "Public note", "hello world")
	if err != nil {
		t.Fatalf("Create: %v", err)
	}

	ps, err := repo.CreatePublicShare(ctx, n.ID)
	if err != nil {
		t.Fatalf("CreatePublicShare: %v", err)
	}
	if len(ps.Token) < 20 {
		t.Errorf("token looks too short to be unguessable: %q", ps.Token)
	}
	if ps.Token == n.ID {
		t.Error("public token must not be derived from the note's own ID")
	}

	view, err := repo.GetByPublicToken(ctx, ps.Token)
	if err != nil {
		t.Fatalf("GetByPublicToken: %v", err)
	}
	if view.Title != "Public note" {
		t.Errorf("Title = %q, want %q", view.Title, "Public note")
	}

	if err := repo.DeletePublicShare(ctx, n.ID); err != nil {
		t.Fatalf("DeletePublicShare: %v", err)
	}
	if _, err := repo.GetByPublicToken(ctx, ps.Token); !errors.Is(err, apperr.ErrNotFound) {
		t.Errorf("GetByPublicToken after revoke: err = %v, want ErrNotFound", err)
	}
}

func TestMentionsAreNotifiedOnlyOnce(t *testing.T) {
	ctx := context.Background()
	repo, userRepo := newTestRepo(t)
	owner := mustCreateUser(t, userRepo, "owner")
	mentioned := mustCreateUser(t, userRepo, "mentioned")

	n, err := repo.Create(ctx, owner.ID, "Note", "hi @mentioned")
	if err != nil {
		t.Fatalf("Create: %v", err)
	}

	existing, err := repo.ExistingMentions(ctx, n.ID)
	if err != nil {
		t.Fatalf("ExistingMentions: %v", err)
	}
	if len(existing) != 0 {
		t.Fatalf("expected no mentions yet, got %v", existing)
	}

	if err := repo.AddMentions(ctx, n.ID, []string{mentioned.ID}); err != nil {
		t.Fatalf("AddMentions: %v", err)
	}

	existing, err = repo.ExistingMentions(ctx, n.ID)
	if err != nil {
		t.Fatalf("ExistingMentions: %v", err)
	}
	if !existing[mentioned.ID] {
		t.Errorf("expected %s to be recorded as mentioned", mentioned.ID)
	}

	// Adding the same mention again must stay idempotent (no duplicate row/error).
	if err := repo.AddMentions(ctx, n.ID, []string{mentioned.ID}); err != nil {
		t.Fatalf("AddMentions (repeat): %v", err)
	}
}

func TestListForViewerReturnsFullMarkdown(t *testing.T) {
	ctx := context.Background()
	repo, userRepo := newTestRepo(t)
	owner := mustCreateUser(t, userRepo, "owner")

	const body = "# Heading\n\nSome **bold** text and a [link](https://example.com)\n\n- one\n- two"
	if _, err := repo.Create(ctx, owner.ID, "Note", body); err != nil {
		t.Fatalf("Create: %v", err)
	}

	page, err := repo.ListForViewer(ctx, owner.ID, "", 10)
	if err != nil {
		t.Fatalf("ListForViewer: %v", err)
	}
	if len(page.Items) != 1 {
		t.Fatalf("got %d items, want 1", len(page.Items))
	}
	if page.Items[0].ContentMarkdown != body {
		t.Errorf("ContentMarkdown = %q, want %q (dashboard feed must not alter markdown)", page.Items[0].ContentMarkdown, body)
	}
}

func TestListForViewerCursorPagination(t *testing.T) {
	ctx := context.Background()
	repo, userRepo := newTestRepo(t)
	owner := mustCreateUser(t, userRepo, "owner")

	const total = 5
	ids := make(map[string]bool, total)
	for i := 0; i < total; i++ {
		n, err := repo.Create(ctx, owner.ID, "Note", "content")
		if err != nil {
			t.Fatalf("Create: %v", err)
		}
		ids[n.ID] = true
		time.Sleep(time.Millisecond) // ensure distinct updated_at ordering
	}

	seen := map[string]bool{}
	cursor := ""
	for pages := 0; ; pages++ {
		if pages > total {
			t.Fatal("pagination did not terminate")
		}
		page, err := repo.ListForViewer(ctx, owner.ID, cursor, 2)
		if err != nil {
			t.Fatalf("ListForViewer: %v", err)
		}
		for _, item := range page.Items {
			if seen[item.ID] {
				t.Fatalf("note %s returned twice across pages", item.ID)
			}
			seen[item.ID] = true
		}
		if page.NextCursor == "" {
			break
		}
		cursor = page.NextCursor
	}

	if len(seen) != total {
		t.Errorf("saw %d notes across pages, want %d", len(seen), total)
	}
	for id := range ids {
		if !seen[id] {
			t.Errorf("note %s was never returned by pagination", id)
		}
	}
}
