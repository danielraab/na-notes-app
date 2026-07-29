package httpapi

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"

	"github.com/danielraab/na-notes-app/backend-go/internal/apperr"
)

func respondJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func respondNoContent(w http.ResponseWriter) {
	w.WriteHeader(http.StatusNoContent)
}

type errorBody struct {
	Error struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	} `json:"error"`
}

func respondError(w http.ResponseWriter, status int, code, message string) {
	var body errorBody
	body.Error.Code = code
	body.Error.Message = message
	respondJSON(w, status, body)
}

// respondDomainError maps the small set of sentinel errors domain
// packages return onto HTTP status codes. Anything else is treated as an
// unexpected server error and logged with detail the client never sees.
func respondDomainError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, apperr.ErrNotFound):
		respondError(w, http.StatusNotFound, "NOT_FOUND", "resource not found")
	case errors.Is(err, apperr.ErrForbidden):
		respondError(w, http.StatusForbidden, "FORBIDDEN", "not permitted")
	case errors.Is(err, apperr.ErrValidation):
		respondError(w, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
	case errors.Is(err, apperr.ErrVersionConflict):
		respondError(w, http.StatusConflict, "VERSION_CONFLICT", "note was modified since you last loaded it")
	default:
		slog.Error("unhandled error", "error", err)
		respondError(w, http.StatusInternalServerError, "INTERNAL", "internal server error")
	}
}

func decodeJSON(r *http.Request, v any) error {
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	return dec.Decode(v)
}
