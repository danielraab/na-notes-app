package httpapi

import "net/http"

func (d *Deps) handlePublicNote(w http.ResponseWriter, r *http.Request) {
	token := r.PathValue("token")
	view, err := d.Notes.GetPublicNote(r.Context(), token)
	if err != nil {
		respondDomainError(w, err)
		return
	}
	respondJSON(w, http.StatusOK, toPublicNoteViewDTO(view))
}
