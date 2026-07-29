package notes

import "time"

type Permission string

const (
	PermissionOwner Permission = "owner"
	PermissionEdit  Permission = "edit"
	PermissionRead  Permission = "read"
)

type Note struct {
	ID              string
	OwnerID         string
	Title           string
	ContentMarkdown string
	Version         int
	IsPublic        bool
	CreatedAt       time.Time
	UpdatedAt       time.Time
	MyPermission    Permission
}

type Summary struct {
	ID           string
	Title        string
	Excerpt      string
	OwnerID      string
	MyPermission Permission
	IsPublic     bool
	UpdatedAt    time.Time
}

type Page struct {
	Items      []Summary
	NextCursor string // empty means no more pages
}

type UserShare struct {
	UserID      string
	DisplayName string
	AvatarURL   string
	Permission  Permission
	CreatedAt   time.Time
}

type PublicShare struct {
	Token     string
	CreatedAt time.Time
}

type PublicNoteView struct {
	Title           string
	ContentMarkdown string
	UpdatedAt       time.Time
}
