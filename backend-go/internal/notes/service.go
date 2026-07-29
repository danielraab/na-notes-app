package notes

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/danielraab/na-notes-app/backend-go/internal/apperr"
	"github.com/danielraab/na-notes-app/backend-go/internal/mail"
	"github.com/danielraab/na-notes-app/backend-go/internal/users"
)

const InitialPageSize = 12

func sampleNote() Summary {
	return Summary{
		ID:           "00000000-0000-0000-0000-000000000000",
		Title:        "Welcome to NA Notes",
		Excerpt:      "Sign in to create your own notes, share them with teammates, and mention people to loop them in.",
		OwnerID:      "00000000-0000-0000-0000-000000000000",
		MyPermission: PermissionRead,
		IsPublic:     true,
		UpdatedAt:    time.Now().UTC(),
	}
}

type Service struct {
	repo    *Repository
	users   *users.Repository
	mailer  *mail.Mailer
	baseURL string // frontend origin, for links in emails and public share URLs
}

func NewService(repo *Repository, users *users.Repository, mailer *mail.Mailer, frontendBaseURL string) *Service {
	return &Service{repo: repo, users: users, mailer: mailer, baseURL: frontendBaseURL}
}

// List returns the dashboard feed. An anonymous viewer (viewerID == "")
// always sees exactly the sample note, per the product spec.
func (s *Service) List(ctx context.Context, viewerID, cursor string, limit int) (Page, error) {
	if viewerID == "" {
		return Page{Items: []Summary{sampleNote()}}, nil
	}
	if limit <= 0 {
		limit = InitialPageSize
	}
	return s.repo.ListForViewer(ctx, viewerID, cursor, limit)
}

// Get fetches a note for viewerID, resolving their effective permission.
// A viewer with no ownership or share record gets ErrNotFound rather than
// ErrForbidden, so the endpoint doesn't reveal that the note exists.
func (s *Service) Get(ctx context.Context, noteID, viewerID string) (Note, error) {
	n, err := s.repo.GetByID(ctx, noteID)
	if err != nil {
		return Note{}, err
	}
	if n.OwnerID == viewerID {
		n.MyPermission = PermissionOwner
		return n, nil
	}
	perm, ok, err := s.repo.SharePermission(ctx, noteID, viewerID)
	if err != nil {
		return Note{}, err
	}
	if !ok {
		return Note{}, apperr.ErrNotFound
	}
	n.MyPermission = perm
	return n, nil
}

func (s *Service) Create(ctx context.Context, ownerID, title, content string, mentionedUserIDs []string) (Note, error) {
	if title == "" {
		return Note{}, fmt.Errorf("%w: title is required", apperr.ErrValidation)
	}
	n, err := s.repo.Create(ctx, ownerID, title, content)
	if err != nil {
		return Note{}, err
	}
	if err := s.notifyMentions(ctx, n, ownerID, nil, mentionedUserIDs); err != nil {
		return Note{}, err
	}
	return n, nil
}

func (s *Service) Update(ctx context.Context, noteID, actorID string, expectedVersion int, title, content string, mentionedUserIDs []string) (Note, error) {
	if title == "" {
		return Note{}, fmt.Errorf("%w: title is required", apperr.ErrValidation)
	}
	current, err := s.Get(ctx, noteID, actorID)
	if err != nil {
		return Note{}, err
	}
	if current.MyPermission != PermissionOwner && current.MyPermission != PermissionEdit {
		return Note{}, apperr.ErrForbidden
	}

	existing, err := s.repo.ExistingMentions(ctx, noteID)
	if err != nil {
		return Note{}, err
	}

	updated, err := s.repo.Update(ctx, noteID, title, content, expectedVersion)
	if err != nil {
		if errors.Is(err, apperr.ErrVersionConflict) {
			updated.MyPermission = current.MyPermission
			return updated, apperr.ErrVersionConflict
		}
		return Note{}, err
	}
	updated.MyPermission = current.MyPermission

	if err := s.notifyMentions(ctx, updated, actorID, existing, mentionedUserIDs); err != nil {
		return Note{}, err
	}
	return updated, nil
}

// notifyMentions records mentionedUserIDs against noteID and emails only
// the ones not already present in alreadyMentioned, so editing a note
// doesn't re-notify people mentioned in an earlier version.
func (s *Service) notifyMentions(ctx context.Context, n Note, actorID string, alreadyMentioned map[string]bool, mentionedUserIDs []string) error {
	if len(mentionedUserIDs) == 0 {
		return nil
	}
	if err := s.repo.AddMentions(ctx, n.ID, mentionedUserIDs); err != nil {
		return err
	}
	actor, err := s.users.GetByID(ctx, actorID)
	if err != nil {
		return err
	}
	noteURL := fmt.Sprintf("%s/notes/%s", s.baseURL, n.ID)
	for _, uid := range mentionedUserIDs {
		if alreadyMentioned[uid] || uid == actorID {
			continue
		}
		mentioned, err := s.users.GetByID(ctx, uid)
		if err != nil {
			continue // unknown/invalid mention target: skip rather than fail the save
		}
		s.mailer.NotifyMentioned(mentioned.Email, actor.DisplayName, n.Title, noteURL)
	}
	return nil
}

func (s *Service) Delete(ctx context.Context, noteID, actorID string) error {
	n, err := s.repo.GetByID(ctx, noteID)
	if err != nil {
		return err
	}
	if n.OwnerID != actorID {
		return apperr.ErrForbidden
	}
	return s.repo.Delete(ctx, noteID)
}

func (s *Service) requireOwner(ctx context.Context, noteID, actorID string) (Note, error) {
	n, err := s.repo.GetByID(ctx, noteID)
	if err != nil {
		return Note{}, err
	}
	if n.OwnerID != actorID {
		return Note{}, apperr.ErrForbidden
	}
	return n, nil
}

func (s *Service) ListShares(ctx context.Context, noteID, actorID string) ([]UserShare, *PublicShare, error) {
	if _, err := s.requireOwner(ctx, noteID, actorID); err != nil {
		return nil, nil, err
	}
	shares, err := s.repo.ListShares(ctx, noteID)
	if err != nil {
		return nil, nil, err
	}
	public, err := s.repo.GetPublicShare(ctx, noteID)
	if err != nil {
		return nil, nil, err
	}
	return shares, public, nil
}

func (s *Service) ShareWithUser(ctx context.Context, noteID, actorID, targetUserID string, permission Permission) (UserShare, error) {
	n, err := s.requireOwner(ctx, noteID, actorID)
	if err != nil {
		return UserShare{}, err
	}
	if targetUserID == actorID {
		return UserShare{}, fmt.Errorf("%w: cannot share a note with yourself", apperr.ErrValidation)
	}
	target, err := s.users.GetByID(ctx, targetUserID)
	if err != nil {
		return UserShare{}, fmt.Errorf("%w: unknown user", apperr.ErrValidation)
	}
	if err := s.repo.UpsertShare(ctx, noteID, targetUserID, permission); err != nil {
		return UserShare{}, err
	}

	actor, err := s.users.GetByID(ctx, actorID)
	if err != nil {
		return UserShare{}, err
	}
	noteURL := fmt.Sprintf("%s/notes/%s", s.baseURL, noteID)
	s.mailer.NotifyNoteShared(target.Email, actor.DisplayName, n.Title, noteURL, permission == PermissionEdit)

	return UserShare{
		UserID:      target.ID,
		DisplayName: target.DisplayName,
		Permission:  permission,
		CreatedAt:   time.Now().UTC(),
	}, nil
}

func (s *Service) RevokeShare(ctx context.Context, noteID, actorID, targetUserID string) error {
	if _, err := s.requireOwner(ctx, noteID, actorID); err != nil {
		return err
	}
	return s.repo.DeleteShare(ctx, noteID, targetUserID)
}

func (s *Service) CreatePublicShare(ctx context.Context, noteID, actorID string) (PublicShare, string, error) {
	if _, err := s.requireOwner(ctx, noteID, actorID); err != nil {
		return PublicShare{}, "", err
	}
	ps, err := s.repo.CreatePublicShare(ctx, noteID)
	if err != nil {
		return PublicShare{}, "", err
	}
	url := fmt.Sprintf("%s/shared/%s", s.baseURL, ps.Token)
	return ps, url, nil
}

func (s *Service) RevokePublicShare(ctx context.Context, noteID, actorID string) error {
	if _, err := s.requireOwner(ctx, noteID, actorID); err != nil {
		return err
	}
	return s.repo.DeletePublicShare(ctx, noteID)
}

func (s *Service) GetPublicNote(ctx context.Context, token string) (PublicNoteView, error) {
	return s.repo.GetByPublicToken(ctx, token)
}
