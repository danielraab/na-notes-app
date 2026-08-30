package notes

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/google/uuid"

	"github.com/danielraab/na-notes-app/backend-go/internal/apperr"
	"github.com/danielraab/na-notes-app/backend-go/internal/db"
	"github.com/danielraab/na-notes-app/backend-go/internal/randtoken"
)

const publicShareTokenBytes = 16 // 128 bits of entropy, see ADR 0009

type Repository struct {
	db *db.DB
}

func NewRepository(sqlDB *db.DB) *Repository {
	return &Repository{db: sqlDB}
}

func (r *Repository) Create(ctx context.Context, ownerID, title, content string) (Note, error) {
	now := time.Now().UTC()
	n := Note{
		ID:              uuid.NewString(),
		OwnerID:         ownerID,
		Title:           title,
		ContentMarkdown: content,
		Version:         1,
		CreatedAt:       now,
		UpdatedAt:       now,
		MyPermission:    PermissionOwner,
	}
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO notes (id, owner_id, title, content_markdown, version, created_at, updated_at)
		 VALUES (?, ?, ?, ?, 1, ?, ?)`,
		n.ID, n.OwnerID, n.Title, n.ContentMarkdown, fmtTime(now), fmtTime(now),
	)
	if err != nil {
		return Note{}, fmt.Errorf("insert note: %w", err)
	}
	return n, nil
}

// GetByID fetches the raw note without regard to who is asking; callers
// (the service layer) are responsible for authorization decisions.
func (r *Repository) GetByID(ctx context.Context, id string) (Note, error) {
	var n Note
	var createdAt, updatedAt string
	var publicNoteID sql.NullString
	err := r.db.QueryRowContext(ctx,
		`SELECT n.id, n.owner_id, n.title, n.content_markdown, n.version, n.created_at, n.updated_at, nps.note_id
		 FROM notes n
		 LEFT JOIN note_public_shares nps ON nps.note_id = n.id
		 WHERE n.id = ?`, id,
	).Scan(&n.ID, &n.OwnerID, &n.Title, &n.ContentMarkdown, &n.Version, &createdAt, &updatedAt, &publicNoteID)
	if err == sql.ErrNoRows {
		return Note{}, apperr.ErrNotFound
	}
	if err != nil {
		return Note{}, fmt.Errorf("select note: %w", err)
	}
	n.IsPublic = publicNoteID.Valid
	n.CreatedAt = parseTime(createdAt)
	n.UpdatedAt = parseTime(updatedAt)
	return n, nil
}

// Update applies an optimistic-concurrency-checked edit (ADR 0008): it
// only succeeds if the row's current version still matches expectedVersion.
func (r *Repository) Update(ctx context.Context, id, title, content string, expectedVersion int) (Note, error) {
	now := fmtTime(time.Now().UTC())
	res, err := r.db.ExecContext(ctx,
		`UPDATE notes SET title = ?, content_markdown = ?, version = version + 1, updated_at = ?
		 WHERE id = ? AND version = ?`,
		title, content, now, id, expectedVersion,
	)
	if err != nil {
		return Note{}, fmt.Errorf("update note: %w", err)
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return Note{}, err
	}
	if affected == 0 {
		current, err := r.GetByID(ctx, id)
		if err != nil {
			return Note{}, err // not found takes precedence over conflict
		}
		// Returned alongside the error so the caller can hand the
		// caller's client the current server copy of the note (409 body).
		return current, apperr.ErrVersionConflict
	}
	return r.GetByID(ctx, id)
}

func (r *Repository) Delete(ctx context.Context, id string) error {
	res, err := r.db.ExecContext(ctx, `DELETE FROM notes WHERE id = ?`, id)
	if err != nil {
		return fmt.Errorf("delete note: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return apperr.ErrNotFound
	}
	return nil
}

// SharePermission returns the explicit share permission granted to
// userID on noteID, if any. It does not consider ownership.
func (r *Repository) SharePermission(ctx context.Context, noteID, userID string) (Permission, bool, error) {
	var p string
	err := r.db.QueryRowContext(ctx,
		`SELECT permission FROM note_shares WHERE note_id = ? AND user_id = ?`, noteID, userID,
	).Scan(&p)
	if err == sql.ErrNoRows {
		return "", false, nil
	}
	if err != nil {
		return "", false, err
	}
	return Permission(p), true, nil
}

// ListForViewer returns a cursor page of notes owned by, or shared with,
// userID, newest-edited first (ADR 0007).
func (r *Repository) ListForViewer(ctx context.Context, userID, cur string, limit int) (Page, error) {
	args := []any{userID, userID, userID, userID}
	whereCursor := ""
	if cur != "" {
		c, err := decodeCursor(cur)
		if err != nil {
			return Page{}, fmt.Errorf("%w: %v", apperr.ErrValidation, err)
		}
		whereCursor = "AND (n.updated_at, n.id) < (?, ?)"
		args = append(args, c.UpdatedAt, c.ID)
	}
	args = append(args, limit+1)

	query := `
		SELECT n.id, n.title, n.content_markdown, n.owner_id, n.updated_at,
		       CASE WHEN n.owner_id = ? THEN 'owner' ELSE ns.permission END AS permission,
		       CASE WHEN nps.note_id IS NOT NULL THEN 1 ELSE 0 END AS is_public
		FROM notes n
		LEFT JOIN note_shares ns ON ns.note_id = n.id AND ns.user_id = ?
		LEFT JOIN note_public_shares nps ON nps.note_id = n.id
		WHERE (n.owner_id = ? OR ns.user_id = ?)
		` + whereCursor + `
		ORDER BY n.updated_at DESC, n.id DESC
		LIMIT ?`

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return Page{}, fmt.Errorf("list notes: %w", err)
	}
	defer rows.Close()

	var items []Summary
	for rows.Next() {
		var s Summary
		var updatedAt, permission string
		var isPublic int
		if err := rows.Scan(&s.ID, &s.Title, &s.ContentMarkdown, &s.OwnerID, &updatedAt, &permission, &isPublic); err != nil {
			return Page{}, err
		}
		s.UpdatedAt = parseTime(updatedAt)
		s.MyPermission = Permission(permission)
		s.IsPublic = isPublic == 1
		items = append(items, s)
	}
	if err := rows.Err(); err != nil {
		return Page{}, err
	}

	page := Page{Items: items}
	if len(items) > limit {
		last := items[limit-1]
		page.Items = items[:limit]
		page.NextCursor = encodeCursor(last.UpdatedAt, last.ID)
	}
	return page, nil
}

func (r *Repository) ListShares(ctx context.Context, noteID string) ([]UserShare, error) {
	rows, err := r.db.QueryContext(ctx,
		`SELECT u.id, u.display_name, COALESCE(u.avatar_url, ''), ns.permission, ns.created_at
		 FROM note_shares ns
		 JOIN users u ON u.id = ns.user_id
		 WHERE ns.note_id = ?
		 ORDER BY ns.created_at`, noteID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []UserShare
	for rows.Next() {
		var s UserShare
		var permission, createdAt string
		if err := rows.Scan(&s.UserID, &s.DisplayName, &s.AvatarURL, &permission, &createdAt); err != nil {
			return nil, err
		}
		s.Permission = Permission(permission)
		s.CreatedAt = parseTime(createdAt)
		out = append(out, s)
	}
	return out, rows.Err()
}

// UpsertShare grants (or changes the permission of) userID's access to
// noteID. The owner explicitly re-sharing an already-shared note still
// triggers a notification email (see service.ShareWithUser) — that's a
// deliberate simplification over tracking new-vs-changed shares.
func (r *Repository) UpsertShare(ctx context.Context, noteID, userID string, permission Permission) error {
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO note_shares (note_id, user_id, permission, created_at)
		 VALUES (?, ?, ?, ?)
		 ON CONFLICT(note_id, user_id) DO UPDATE SET permission = excluded.permission`,
		noteID, userID, string(permission), fmtTime(time.Now().UTC()),
	)
	if err != nil {
		return fmt.Errorf("upsert share: %w", err)
	}
	return nil
}

func (r *Repository) DeleteShare(ctx context.Context, noteID, userID string) error {
	res, err := r.db.ExecContext(ctx, `DELETE FROM note_shares WHERE note_id = ? AND user_id = ?`, noteID, userID)
	if err != nil {
		return err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return apperr.ErrNotFound
	}
	return nil
}

func (r *Repository) GetPublicShare(ctx context.Context, noteID string) (*PublicShare, error) {
	var ps PublicShare
	var createdAt string
	err := r.db.QueryRowContext(ctx,
		`SELECT token, created_at FROM note_public_shares WHERE note_id = ?`, noteID,
	).Scan(&ps.Token, &createdAt)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	ps.CreatedAt = parseTime(createdAt)
	return &ps, nil
}

// CreatePublicShare (re)publishes noteID with a freshly generated,
// unguessable token, replacing any previous token (ADR 0009).
func (r *Repository) CreatePublicShare(ctx context.Context, noteID string) (PublicShare, error) {
	token, err := randtoken.New(publicShareTokenBytes)
	if err != nil {
		return PublicShare{}, fmt.Errorf("generate share token: %w", err)
	}
	now := time.Now().UTC()
	_, err = r.db.ExecContext(ctx,
		`INSERT INTO note_public_shares (note_id, token, created_at) VALUES (?, ?, ?)
		 ON CONFLICT(note_id) DO UPDATE SET token = excluded.token, created_at = excluded.created_at`,
		noteID, token, fmtTime(now),
	)
	if err != nil {
		return PublicShare{}, fmt.Errorf("create public share: %w", err)
	}
	return PublicShare{Token: token, CreatedAt: now}, nil
}

func (r *Repository) DeletePublicShare(ctx context.Context, noteID string) error {
	res, err := r.db.ExecContext(ctx, `DELETE FROM note_public_shares WHERE note_id = ?`, noteID)
	if err != nil {
		return err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return apperr.ErrNotFound
	}
	return nil
}

func (r *Repository) GetByPublicToken(ctx context.Context, token string) (PublicNoteView, error) {
	var v PublicNoteView
	var updatedAt string
	err := r.db.QueryRowContext(ctx,
		`SELECT n.title, n.content_markdown, n.updated_at
		 FROM note_public_shares nps
		 JOIN notes n ON n.id = nps.note_id
		 WHERE nps.token = ?`, token,
	).Scan(&v.Title, &v.ContentMarkdown, &updatedAt)
	if err == sql.ErrNoRows {
		return PublicNoteView{}, apperr.ErrNotFound
	}
	if err != nil {
		return PublicNoteView{}, err
	}
	v.UpdatedAt = parseTime(updatedAt)
	return v, nil
}

// ExistingMentions returns the set of user IDs already recorded as
// mentioned in noteID, so the caller can notify only newly added mentions.
func (r *Repository) ExistingMentions(ctx context.Context, noteID string) (map[string]bool, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT user_id FROM note_mentions WHERE note_id = ?`, noteID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	set := map[string]bool{}
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		set[id] = true
	}
	return set, rows.Err()
}

func (r *Repository) AddMentions(ctx context.Context, noteID string, userIDs []string) error {
	now := fmtTime(time.Now().UTC())
	for _, uid := range userIDs {
		if _, err := r.db.ExecContext(ctx,
			`INSERT INTO note_mentions (note_id, user_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING`,
			noteID, uid, now,
		); err != nil {
			return err
		}
	}
	return nil
}

func fmtTime(t time.Time) string {
	return t.Format(time.RFC3339Nano)
}

func parseTime(s string) time.Time {
	t, _ := time.Parse(time.RFC3339Nano, s)
	return t
}
