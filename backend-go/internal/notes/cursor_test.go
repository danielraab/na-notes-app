package notes

import (
	"testing"
	"time"
)

func TestCursorRoundTrip(t *testing.T) {
	want := time.Date(2026, 7, 29, 12, 0, 0, 0, time.UTC)
	encoded := encodeCursor(want, "note-123")

	got, err := decodeCursor(encoded)
	if err != nil {
		t.Fatalf("decodeCursor: %v", err)
	}
	if got.ID != "note-123" {
		t.Errorf("ID = %q, want %q", got.ID, "note-123")
	}
	gotTime, err := time.Parse(time.RFC3339Nano, got.UpdatedAt)
	if err != nil {
		t.Fatalf("parse decoded time: %v", err)
	}
	if !gotTime.Equal(want) {
		t.Errorf("UpdatedAt = %v, want %v", gotTime, want)
	}
}

func TestDecodeCursorRejectsGarbage(t *testing.T) {
	if _, err := decodeCursor("not-valid-base64!!"); err == nil {
		t.Error("expected an error for invalid base64, got nil")
	}
	if _, err := decodeCursor("e30"); err == nil { // base64("{}"), missing fields
		t.Error("expected an error for cursor missing fields, got nil")
	}
}
