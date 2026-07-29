// DTOs mirror the schemas in /openapi/openapi.yaml exactly (field names,
// casing, nullability) — that file is the source of truth; if these
// diverge from it, the spec is wrong or this code is.
package httpapi

import (
	"time"

	"github.com/danielraab/na-notes-app/backend-go/internal/notes"
	"github.com/danielraab/na-notes-app/backend-go/internal/users"
)

type userDTO struct {
	ID          string  `json:"id"`
	Email       string  `json:"email"`
	DisplayName string  `json:"displayName"`
	AvatarURL   *string `json:"avatarUrl"`
}

func toUserDTO(u users.User) userDTO {
	return userDTO{
		ID:          u.ID,
		Email:       u.Email,
		DisplayName: u.DisplayName,
		AvatarURL:   nullableString(u.AvatarURL.String, u.AvatarURL.Valid),
	}
}

type userSummaryDTO struct {
	ID          string  `json:"id"`
	DisplayName string  `json:"displayName"`
	AvatarURL   *string `json:"avatarUrl"`
}

func toUserSummaryDTO(u users.Summary) userSummaryDTO {
	return userSummaryDTO{
		ID:          u.ID,
		DisplayName: u.DisplayName,
		AvatarURL:   nullableString(u.AvatarURL.String, u.AvatarURL.Valid),
	}
}

type noteInputDTO struct {
	Title            string   `json:"title"`
	ContentMarkdown  string   `json:"contentMarkdown"`
	MentionedUserIDs []string `json:"mentionedUserIds"`
}

type noteDTO struct {
	ID              string    `json:"id"`
	Title           string    `json:"title"`
	ContentMarkdown string    `json:"contentMarkdown"`
	OwnerID         string    `json:"ownerId"`
	Version         int       `json:"version"`
	MyPermission    string    `json:"myPermission"`
	IsPublic        bool      `json:"isPublic"`
	CreatedAt       time.Time `json:"createdAt"`
	UpdatedAt       time.Time `json:"updatedAt"`
}

func toNoteDTO(n notes.Note) noteDTO {
	return noteDTO{
		ID:              n.ID,
		Title:           n.Title,
		ContentMarkdown: n.ContentMarkdown,
		OwnerID:         n.OwnerID,
		Version:         n.Version,
		MyPermission:    string(n.MyPermission),
		IsPublic:        n.IsPublic,
		CreatedAt:       n.CreatedAt,
		UpdatedAt:       n.UpdatedAt,
	}
}

type noteSummaryDTO struct {
	ID           string    `json:"id"`
	Title        string    `json:"title"`
	Excerpt      string    `json:"excerpt"`
	OwnerID      string    `json:"ownerId"`
	MyPermission string    `json:"myPermission"`
	IsPublic     bool      `json:"isPublic"`
	UpdatedAt    time.Time `json:"updatedAt"`
}

func toNoteSummaryDTO(s notes.Summary) noteSummaryDTO {
	return noteSummaryDTO{
		ID:           s.ID,
		Title:        s.Title,
		Excerpt:      s.Excerpt,
		OwnerID:      s.OwnerID,
		MyPermission: string(s.MyPermission),
		IsPublic:     s.IsPublic,
		UpdatedAt:    s.UpdatedAt,
	}
}

type notePageDTO struct {
	Items      []noteSummaryDTO `json:"items"`
	NextCursor *string          `json:"nextCursor"`
}

func toNotePageDTO(p notes.Page) notePageDTO {
	items := make([]noteSummaryDTO, 0, len(p.Items))
	for _, s := range p.Items {
		items = append(items, toNoteSummaryDTO(s))
	}
	return notePageDTO{Items: items, NextCursor: nullableString(p.NextCursor, p.NextCursor != "")}
}

type userShareDTO struct {
	User       userSummaryDTO `json:"user"`
	Permission string         `json:"permission"`
	CreatedAt  time.Time      `json:"createdAt"`
}

func toUserShareDTO(s notes.UserShare) userShareDTO {
	return userShareDTO{
		User: userSummaryDTO{
			ID:          s.UserID,
			DisplayName: s.DisplayName,
			AvatarURL:   nullableString(s.AvatarURL, s.AvatarURL != ""),
		},
		Permission: string(s.Permission),
		CreatedAt:  s.CreatedAt,
	}
}

type publicShareDTO struct {
	Token     string    `json:"token"`
	URL       string    `json:"url"`
	CreatedAt time.Time `json:"createdAt"`
}

func toPublicShareDTO(ps notes.PublicShare, url string) publicShareDTO {
	return publicShareDTO{Token: ps.Token, URL: url, CreatedAt: ps.CreatedAt}
}

type publicNoteViewDTO struct {
	Title           string    `json:"title"`
	ContentMarkdown string    `json:"contentMarkdown"`
	UpdatedAt       time.Time `json:"updatedAt"`
}

func toPublicNoteViewDTO(v notes.PublicNoteView) publicNoteViewDTO {
	return publicNoteViewDTO{Title: v.Title, ContentMarkdown: v.ContentMarkdown, UpdatedAt: v.UpdatedAt}
}

func nullableString(s string, valid bool) *string {
	if !valid {
		return nil
	}
	return &s
}
