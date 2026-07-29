package httpapi

import (
	"context"
	"crypto/subtle"
	"log/slog"
	"net/http"
	"time"
)

type ctxKey int

const (
	ctxUserID ctxKey = iota
	ctxCSRFToken
)

// authedHandler receives the current user's ID, or "" for an anonymous
// caller under withOptionalAuth.
type authedHandler func(w http.ResponseWriter, r *http.Request, userID string)

func userIDFromContext(ctx context.Context) (string, bool) {
	v, ok := ctx.Value(ctxUserID).(string)
	return v, ok
}

// sessionContextMiddleware resolves the session cookie (if any) once per
// request and stores the result in context, so downstream middleware/
// handlers never need to touch the session store themselves.
func (d *Deps) sessionContextMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		cookie, err := r.Cookie(SessionCookieName)
		if err != nil {
			next.ServeHTTP(w, r)
			return
		}
		sess, err := d.AuthStore.GetSession(r.Context(), cookie.Value)
		if err != nil {
			next.ServeHTTP(w, r)
			return
		}
		ctx := context.WithValue(r.Context(), ctxUserID, sess.UserID)
		ctx = context.WithValue(ctx, ctxCSRFToken, sess.CSRFToken)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// withAuth rejects unauthenticated requests with 401 before the handler runs.
func withAuth(h authedHandler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, ok := userIDFromContext(r.Context())
		if !ok {
			respondError(w, http.StatusUnauthorized, "UNAUTHENTICATED", "login required")
			return
		}
		h(w, r, userID)
	}
}

// withOptionalAuth passes "" as userID for anonymous callers instead of
// rejecting the request, for endpoints with public behavior (dashboard
// feed, current-user check).
func withOptionalAuth(h authedHandler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := userIDFromContext(r.Context())
		h(w, r, userID)
	}
}

func isStateChanging(method string) bool {
	switch method {
	case http.MethodPost, http.MethodPut, http.MethodPatch, http.MethodDelete:
		return true
	default:
		return false
	}
}

// csrfMiddleware enforces the double-submit cookie pattern (ADR 0005) on
// state-changing requests. Requests without a session are let through
// unchecked here — withAuth rejects them with 401, which is the more
// useful error for a caller that was never going to be authorized anyway.
func csrfMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !isStateChanging(r.Method) {
			next.ServeHTTP(w, r)
			return
		}
		expected, ok := r.Context().Value(ctxCSRFToken).(string)
		if !ok {
			next.ServeHTTP(w, r)
			return
		}
		got := r.Header.Get(CSRFHeaderName)
		if got == "" || subtle.ConstantTimeCompare([]byte(got), []byte(expected)) != 1 {
			respondError(w, http.StatusForbidden, "CSRF_REJECTED", "missing or invalid CSRF token")
			return
		}
		next.ServeHTTP(w, r)
	})
}

// corsMiddleware only ever reflects an explicitly allow-listed origin
// (ADR 0005) — it never combines a wildcard origin with credentials.
func (d *Deps) corsMiddleware(next http.Handler) http.Handler {
	allowed := make(map[string]bool, len(d.Config.AllowedOrigins))
	for _, o := range d.Config.AllowedOrigins {
		allowed[o] = true
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		if origin != "" && allowed[origin] {
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Access-Control-Allow-Credentials", "true")
			w.Header().Set("Vary", "Origin")
		}
		if r.Method == http.MethodOptions {
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, "+CSRFHeaderName)
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func requestLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rw := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(rw, r)
		slog.Info("request", "method", r.Method, "path", r.URL.Path, "status", rw.status, "duration", time.Since(start))
	})
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(status int) {
	r.status = status
	r.ResponseWriter.WriteHeader(status)
}
