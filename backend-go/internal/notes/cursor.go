package notes

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"time"
)

// cursor encodes the stable sort key (updated_at, id) used to page
// through the notes feed, per /docs/adr/0007-cursor-pagination.md. It is
// opaque to clients; only this package constructs or interprets it.
type cursor struct {
	UpdatedAt string `json:"u"`
	ID        string `json:"i"`
}

func encodeCursor(updatedAt time.Time, id string) string {
	c := cursor{UpdatedAt: updatedAt.UTC().Format(time.RFC3339Nano), ID: id}
	b, _ := json.Marshal(c)
	return base64.RawURLEncoding.EncodeToString(b)
}

func decodeCursor(s string) (cursor, error) {
	b, err := base64.RawURLEncoding.DecodeString(s)
	if err != nil {
		return cursor{}, fmt.Errorf("invalid cursor: %w", err)
	}
	var c cursor
	if err := json.Unmarshal(b, &c); err != nil {
		return cursor{}, fmt.Errorf("invalid cursor: %w", err)
	}
	if c.UpdatedAt == "" || c.ID == "" {
		return cursor{}, fmt.Errorf("invalid cursor")
	}
	return c, nil
}
