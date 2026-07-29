package httpapi

import (
	"net/http"
	"strconv"
)

const maxUserSearchLimit = 25

func (d *Deps) handleUserSearch(w http.ResponseWriter, r *http.Request, userID string) {
	q := r.URL.Query().Get("q")
	if q == "" {
		respondError(w, http.StatusBadRequest, "VALIDATION_ERROR", "q is required")
		return
	}

	limit := 10
	if raw := r.URL.Query().Get("limit"); raw != "" {
		v, err := strconv.Atoi(raw)
		if err != nil || v <= 0 {
			respondError(w, http.StatusBadRequest, "VALIDATION_ERROR", "limit must be a positive integer")
			return
		}
		limit = v
	}
	if limit > maxUserSearchLimit {
		limit = maxUserSearchLimit
	}

	results, err := d.Users.Search(r.Context(), userID, q, limit)
	if err != nil {
		respondDomainError(w, err)
		return
	}

	dtos := make([]userSummaryDTO, 0, len(results))
	for _, u := range results {
		dtos = append(dtos, toUserSummaryDTO(u))
	}
	respondJSON(w, http.StatusOK, dtos)
}
