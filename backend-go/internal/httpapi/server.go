// Package httpapi wires the REST API defined in /openapi/openapi.yaml:
// routing, CORS/CSRF/session middleware, and request/response mapping.
// Domain logic itself lives in internal/notes, internal/users, internal/auth.
package httpapi

import (
	"net/http"

	"github.com/danielraab/na-notes-app/backend-go/internal/auth"
	"github.com/danielraab/na-notes-app/backend-go/internal/config"
	"github.com/danielraab/na-notes-app/backend-go/internal/notes"
	"github.com/danielraab/na-notes-app/backend-go/internal/users"
)

// Deps holds everything the HTTP layer needs. It has no behavior of its
// own beyond being a container passed to route registration.
type Deps struct {
	Config    *config.Config
	AuthStore *auth.Store
	OIDC      *auth.OIDC
	Users     *users.Repository
	Notes     *notes.Service
}

func NewRouter(d *Deps) http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /healthz", handleHealth)

	mux.HandleFunc("GET /api/auth/login", d.handleLogin)
	mux.HandleFunc("GET /api/auth/callback", d.handleCallback)
	mux.HandleFunc("POST /api/auth/logout", withAuth(d.handleLogout))
	mux.HandleFunc("GET /api/auth/me", withOptionalAuth(d.handleMe))

	mux.HandleFunc("GET /api/users/search", withAuth(d.handleUserSearch))

	mux.HandleFunc("GET /api/notes", withOptionalAuth(d.handleListNotes))
	mux.HandleFunc("POST /api/notes", withAuth(d.handleCreateNote))
	mux.HandleFunc("GET /api/notes/{noteId}", withAuth(d.handleGetNote))
	mux.HandleFunc("PUT /api/notes/{noteId}", withAuth(d.handleUpdateNote))
	mux.HandleFunc("DELETE /api/notes/{noteId}", withAuth(d.handleDeleteNote))

	mux.HandleFunc("GET /api/notes/{noteId}/shares", withAuth(d.handleListShares))
	mux.HandleFunc("POST /api/notes/{noteId}/shares", withAuth(d.handleCreateShare))
	mux.HandleFunc("DELETE /api/notes/{noteId}/shares/{userId}", withAuth(d.handleDeleteShare))

	mux.HandleFunc("POST /api/notes/{noteId}/public-share", withAuth(d.handleCreatePublicShare))
	mux.HandleFunc("DELETE /api/notes/{noteId}/public-share", withAuth(d.handleDeletePublicShare))

	mux.HandleFunc("GET /api/public/notes/{token}", d.handlePublicNote)

	var handler http.Handler = mux
	handler = csrfMiddleware(handler)
	handler = d.sessionContextMiddleware(handler)
	handler = d.corsMiddleware(handler)
	handler = requestLogger(handler)
	return handler
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
}
