package httpapi

import (
	"errors"
	"net/http"
	"strconv"

	"github.com/danielraab/na-notes-app/backend-go/internal/apperr"
)

const maxPageLimit = 50

func (d *Deps) handleListNotes(w http.ResponseWriter, r *http.Request, userID string) {
	cursor := r.URL.Query().Get("cursor")
	limit := 12
	if raw := r.URL.Query().Get("limit"); raw != "" {
		v, err := strconv.Atoi(raw)
		if err != nil || v <= 0 {
			respondError(w, http.StatusBadRequest, "VALIDATION_ERROR", "limit must be a positive integer")
			return
		}
		limit = v
	}
	if limit > maxPageLimit {
		limit = maxPageLimit
	}

	page, err := d.Notes.List(r.Context(), userID, cursor, limit)
	if err != nil {
		respondDomainError(w, err)
		return
	}
	respondJSON(w, http.StatusOK, toNotePageDTO(page))
}

func (d *Deps) handleCreateNote(w http.ResponseWriter, r *http.Request, userID string) {
	var in noteInputDTO
	if err := decodeJSON(r, &in); err != nil {
		respondError(w, http.StatusBadRequest, "VALIDATION_ERROR", "invalid request body")
		return
	}

	n, err := d.Notes.Create(r.Context(), userID, in.Title, in.ContentMarkdown, in.MentionedUserIDs)
	if err != nil {
		respondDomainError(w, err)
		return
	}
	respondJSON(w, http.StatusCreated, toNoteDTO(n))
}

func (d *Deps) handleGetNote(w http.ResponseWriter, r *http.Request, userID string) {
	noteID := r.PathValue("noteId")
	n, err := d.Notes.Get(r.Context(), noteID, userID)
	if err != nil {
		respondDomainError(w, err)
		return
	}
	respondJSON(w, http.StatusOK, toNoteDTO(n))
}

func (d *Deps) handleUpdateNote(w http.ResponseWriter, r *http.Request, userID string) {
	noteID := r.PathValue("noteId")

	ifMatch := r.Header.Get("If-Match")
	expectedVersion, err := strconv.Atoi(ifMatch)
	if err != nil {
		respondError(w, http.StatusBadRequest, "VALIDATION_ERROR", "If-Match header must be the note's current version")
		return
	}

	var in noteInputDTO
	if err := decodeJSON(r, &in); err != nil {
		respondError(w, http.StatusBadRequest, "VALIDATION_ERROR", "invalid request body")
		return
	}

	n, err := d.Notes.Update(r.Context(), noteID, userID, expectedVersion, in.Title, in.ContentMarkdown, in.MentionedUserIDs)
	if err != nil {
		if errors.Is(err, apperr.ErrVersionConflict) {
			respondJSON(w, http.StatusConflict, toNoteDTO(n))
			return
		}
		respondDomainError(w, err)
		return
	}
	respondJSON(w, http.StatusOK, toNoteDTO(n))
}

func (d *Deps) handleDeleteNote(w http.ResponseWriter, r *http.Request, userID string) {
	noteID := r.PathValue("noteId")
	if err := d.Notes.Delete(r.Context(), noteID, userID); err != nil {
		respondDomainError(w, err)
		return
	}
	respondNoContent(w)
}
