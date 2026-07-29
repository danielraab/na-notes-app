// Package users manages user accounts. Accounts are created lazily on
// first successful OIDC login; there is no separate registration flow.
package users

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/google/uuid"
)

type User struct {
	ID          string
	Email       string
	DisplayName string
	AvatarURL   sql.NullString
	CreatedAt   time.Time
}

type Summary struct {
	ID          string
	DisplayName string
	AvatarURL   sql.NullString
}

type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

// UpsertFromOIDC creates the user on first login, or refreshes their
// profile fields (display name/avatar can change at the identity
// provider) on subsequent logins. Matching is on the stable OIDC subject,
// never on email alone, since some providers allow email reuse/change.
func (r *Repository) UpsertFromOIDC(ctx context.Context, subject, email, displayName, avatarURL string) (User, error) {
	now := time.Now().UTC()

	var u User
	var createdAt string
	err := r.db.QueryRowContext(ctx,
		`SELECT id, email, display_name, avatar_url, created_at FROM users WHERE oidc_subject = ?`,
		subject,
	).Scan(&u.ID, &u.Email, &u.DisplayName, &u.AvatarURL, &createdAt)

	if err == sql.ErrNoRows {
		u = User{
			ID:          uuid.NewString(),
			Email:       email,
			DisplayName: displayName,
			AvatarURL:   sql.NullString{String: avatarURL, Valid: avatarURL != ""},
			CreatedAt:   now,
		}
		_, err = r.db.ExecContext(ctx,
			`INSERT INTO users (id, oidc_subject, email, display_name, avatar_url, created_at) VALUES (?, ?, ?, ?, ?, ?)`,
			u.ID, subject, u.Email, u.DisplayName, u.AvatarURL, u.CreatedAt.Format(time.RFC3339Nano),
		)
		if err != nil {
			return User{}, fmt.Errorf("insert user: %w", err)
		}
		return u, nil
	}
	if err != nil {
		return User{}, fmt.Errorf("lookup user: %w", err)
	}
	u.CreatedAt = parseTime(createdAt)

	_, err = r.db.ExecContext(ctx,
		`UPDATE users SET email = ?, display_name = ?, avatar_url = ? WHERE id = ?`,
		email, displayName, sql.NullString{String: avatarURL, Valid: avatarURL != ""}, u.ID,
	)
	if err != nil {
		return User{}, fmt.Errorf("update user: %w", err)
	}
	u.Email = email
	u.DisplayName = displayName
	u.AvatarURL = sql.NullString{String: avatarURL, Valid: avatarURL != ""}
	return u, nil
}

func (r *Repository) GetByID(ctx context.Context, id string) (User, error) {
	var u User
	var createdAt string
	err := r.db.QueryRowContext(ctx,
		`SELECT id, email, display_name, avatar_url, created_at FROM users WHERE id = ?`, id,
	).Scan(&u.ID, &u.Email, &u.DisplayName, &u.AvatarURL, &createdAt)
	if err != nil {
		return User{}, err
	}
	u.CreatedAt = parseTime(createdAt)
	return u, nil
}

func parseTime(s string) time.Time {
	t, _ := time.Parse(time.RFC3339Nano, s)
	return t
}

// Search returns users whose display name or email starts with q,
// excluding the caller, for mention/share autocomplete.
func (r *Repository) Search(ctx context.Context, excludeUserID, q string, limit int) ([]Summary, error) {
	like := q + "%"
	rows, err := r.db.QueryContext(ctx,
		`SELECT id, display_name, avatar_url FROM users
		 WHERE id != ? AND (display_name LIKE ? OR email LIKE ?)
		 ORDER BY display_name LIMIT ?`,
		excludeUserID, like, like, limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []Summary
	for rows.Next() {
		var s Summary
		if err := rows.Scan(&s.ID, &s.DisplayName, &s.AvatarURL); err != nil {
			return nil, err
		}
		out = append(out, s)
	}
	return out, rows.Err()
}
