package httpapi

import (
	"net/http"

	"github.com/danielraab/na-notes-app/backend-go/internal/notes"
)

func (d *Deps) handleListShares(w http.ResponseWriter, r *http.Request, userID string) {
	noteID := r.PathValue("noteId")
	shares, public, err := d.Notes.ListShares(r.Context(), noteID, userID)
	if err != nil {
		respondDomainError(w, err)
		return
	}

	userShares := make([]userShareDTO, 0, len(shares))
	for _, s := range shares {
		userShares = append(userShares, toUserShareDTO(s))
	}

	var publicDTO *publicShareDTO
	if public != nil {
		dto := toPublicShareDTO(*public, d.Config.FrontendURL+"/shared/"+public.Token)
		publicDTO = &dto
	}

	respondJSON(w, http.StatusOK, struct {
		UserShares  []userShareDTO  `json:"userShares"`
		PublicShare *publicShareDTO `json:"publicShare"`
	}{UserShares: userShares, PublicShare: publicDTO})
}

func (d *Deps) handleCreateShare(w http.ResponseWriter, r *http.Request, userID string) {
	noteID := r.PathValue("noteId")

	var in struct {
		UserID     string `json:"userId"`
		Permission string `json:"permission"`
	}
	if err := decodeJSON(r, &in); err != nil {
		respondError(w, http.StatusBadRequest, "VALIDATION_ERROR", "invalid request body")
		return
	}
	if in.Permission != string(notes.PermissionRead) && in.Permission != string(notes.PermissionEdit) {
		respondError(w, http.StatusBadRequest, "VALIDATION_ERROR", "permission must be 'read' or 'edit'")
		return
	}

	share, err := d.Notes.ShareWithUser(r.Context(), noteID, userID, in.UserID, notes.Permission(in.Permission))
	if err != nil {
		respondDomainError(w, err)
		return
	}
	respondJSON(w, http.StatusCreated, toUserShareDTO(share))
}

func (d *Deps) handleDeleteShare(w http.ResponseWriter, r *http.Request, userID string) {
	noteID := r.PathValue("noteId")
	targetUserID := r.PathValue("userId")
	if err := d.Notes.RevokeShare(r.Context(), noteID, userID, targetUserID); err != nil {
		respondDomainError(w, err)
		return
	}
	respondNoContent(w)
}

func (d *Deps) handleCreatePublicShare(w http.ResponseWriter, r *http.Request, userID string) {
	noteID := r.PathValue("noteId")
	ps, url, err := d.Notes.CreatePublicShare(r.Context(), noteID, userID)
	if err != nil {
		respondDomainError(w, err)
		return
	}
	respondJSON(w, http.StatusCreated, toPublicShareDTO(ps, url))
}

func (d *Deps) handleDeletePublicShare(w http.ResponseWriter, r *http.Request, userID string) {
	noteID := r.PathValue("noteId")
	if err := d.Notes.RevokePublicShare(r.Context(), noteID, userID); err != nil {
		respondDomainError(w, err)
		return
	}
	respondNoContent(w)
}
