// Package auth implements the OIDC authorization-code+PKCE login flow
// and the server-side session it creates (ADR 0004).
package auth

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/danielraab/na-notes-app/backend-go/internal/apperr"
	"github.com/danielraab/na-notes-app/backend-go/internal/randtoken"
)

const (
	sessionIDBytes    = 32
	csrfTokenBytes    = 32
	sessionTTL        = 7 * 24 * time.Hour
	oidcRequestTTL    = 10 * time.Minute
	oidcStateBytes    = 24
	codeVerifierBytes = 32
)

type Session struct {
	ID        string
	UserID    string
	CSRFToken string
	ExpiresAt time.Time
}

// OIDCRequest is the server-side record of an in-flight login, keyed by
// the OAuth `state`. The PKCE code_verifier must never be exposed to the
// browser, so it's kept here rather than in a client-readable cookie.
type OIDCRequest struct {
	State        string
	CodeVerifier string
	RedirectTo   string
}

type Store struct {
	db *sql.DB
}

func NewStore(db *sql.DB) *Store {
	return &Store{db: db}
}

func (s *Store) CreateSession(ctx context.Context, userID string) (Session, error) {
	id, err := randtoken.New(sessionIDBytes)
	if err != nil {
		return Session{}, err
	}
	csrf, err := randtoken.New(csrfTokenBytes)
	if err != nil {
		return Session{}, err
	}
	now := time.Now().UTC()
	expiresAt := now.Add(sessionTTL)
	_, err = s.db.ExecContext(ctx,
		`INSERT INTO sessions (id, user_id, csrf_token, expires_at, created_at) VALUES (?, ?, ?, ?, ?)`,
		id, userID, csrf, expiresAt.Format(time.RFC3339Nano), now.Format(time.RFC3339Nano),
	)
	if err != nil {
		return Session{}, fmt.Errorf("create session: %w", err)
	}
	return Session{ID: id, UserID: userID, CSRFToken: csrf, ExpiresAt: expiresAt}, nil
}

func (s *Store) GetSession(ctx context.Context, id string) (Session, error) {
	var sess Session
	var expiresAt string
	err := s.db.QueryRowContext(ctx,
		`SELECT id, user_id, csrf_token, expires_at FROM sessions WHERE id = ?`, id,
	).Scan(&sess.ID, &sess.UserID, &sess.CSRFToken, &expiresAt)
	if err == sql.ErrNoRows {
		return Session{}, apperr.ErrNotFound
	}
	if err != nil {
		return Session{}, err
	}
	sess.ExpiresAt, _ = time.Parse(time.RFC3339Nano, expiresAt)
	if time.Now().UTC().After(sess.ExpiresAt) {
		_ = s.DeleteSession(ctx, id)
		return Session{}, apperr.ErrNotFound
	}
	return sess, nil
}

func (s *Store) DeleteSession(ctx context.Context, id string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE id = ?`, id)
	return err
}

// CreateOIDCRequest starts a login attempt. It also opportunistically
// clears expired requests, since they're otherwise never cleaned up
// (abandoned logins are the only source of them, and volume is low).
func (s *Store) CreateOIDCRequest(ctx context.Context, redirectTo string) (OIDCRequest, error) {
	if _, err := s.db.ExecContext(ctx, `DELETE FROM oidc_requests WHERE expires_at < ?`, time.Now().UTC().Format(time.RFC3339Nano)); err != nil {
		return OIDCRequest{}, err
	}

	state, err := randtoken.New(oidcStateBytes)
	if err != nil {
		return OIDCRequest{}, err
	}
	verifier, err := randtoken.New(codeVerifierBytes)
	if err != nil {
		return OIDCRequest{}, err
	}
	expiresAt := time.Now().UTC().Add(oidcRequestTTL)
	_, err = s.db.ExecContext(ctx,
		`INSERT INTO oidc_requests (state, code_verifier, redirect_to, expires_at) VALUES (?, ?, ?, ?)`,
		state, verifier, redirectTo, expiresAt.Format(time.RFC3339Nano),
	)
	if err != nil {
		return OIDCRequest{}, fmt.Errorf("create oidc request: %w", err)
	}
	return OIDCRequest{State: state, CodeVerifier: verifier, RedirectTo: redirectTo}, nil
}

// ConsumeOIDCRequest looks up and deletes the request in one step: a
// state value must only ever be usable once.
func (s *Store) ConsumeOIDCRequest(ctx context.Context, state string) (OIDCRequest, error) {
	var req OIDCRequest
	var expiresAt string
	err := s.db.QueryRowContext(ctx,
		`SELECT state, code_verifier, redirect_to, expires_at FROM oidc_requests WHERE state = ?`, state,
	).Scan(&req.State, &req.CodeVerifier, &req.RedirectTo, &expiresAt)
	if err == sql.ErrNoRows {
		return OIDCRequest{}, apperr.ErrNotFound
	}
	if err != nil {
		return OIDCRequest{}, err
	}
	if _, err := s.db.ExecContext(ctx, `DELETE FROM oidc_requests WHERE state = ?`, state); err != nil {
		return OIDCRequest{}, err
	}
	exp, _ := time.Parse(time.RFC3339Nano, expiresAt)
	if time.Now().UTC().After(exp) {
		return OIDCRequest{}, apperr.ErrNotFound
	}
	return req, nil
}
