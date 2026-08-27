package httpapi

import (
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/danielraab/na-notes-app/backend-go/internal/apperr"
)

// isSafeRedirectPath restricts post-login redirects to an in-app path, to
// avoid the login flow being used as an open redirect.
func isSafeRedirectPath(p string) bool {
	return p != "" && strings.HasPrefix(p, "/") && !strings.HasPrefix(p, "//")
}

func (d *Deps) handleLogin(w http.ResponseWriter, r *http.Request) {
	redirectTo := r.URL.Query().Get("redirectTo")
	if !isSafeRedirectPath(redirectTo) {
		redirectTo = "/"
	}

	req, err := d.AuthStore.CreateOIDCRequest(r.Context(), redirectTo)
	if err != nil {
		respondDomainError(w, err)
		return
	}

	http.Redirect(w, r, d.OIDC.AuthCodeURL(req.State, req.CodeVerifier), http.StatusFound)
}

func (d *Deps) handleCallback(w http.ResponseWriter, r *http.Request) {
	code := r.URL.Query().Get("code")
	state := r.URL.Query().Get("state")
	if code == "" || state == "" {
		respondError(w, http.StatusBadRequest, "VALIDATION_ERROR", "missing code or state")
		return
	}

	req, err := d.AuthStore.ConsumeOIDCRequest(r.Context(), state)
	if err != nil {
		respondError(w, http.StatusBadRequest, "INVALID_STATE", "login request expired or was already used")
		return
	}

	claims, err := d.OIDC.Exchange(r.Context(), code, req.CodeVerifier)
	if err != nil {
		slog.Error("oidc exchange failed", "error", err)
		respondError(w, http.StatusBadGateway, "OIDC_EXCHANGE_FAILED", "could not complete login with identity provider")
		return
	}

	user, err := d.Users.UpsertFromOIDC(r.Context(), claims.Subject, claims.Email, claims.DisplayName, claims.AvatarURL)
	if err != nil {
		respondDomainError(w, err)
		return
	}

	session, err := d.AuthStore.CreateSession(r.Context(), user.ID)
	if err != nil {
		respondDomainError(w, err)
		return
	}

	secure := strings.HasPrefix(d.Config.PublicBaseURL, "https://")
	maxAge := int(time.Until(session.ExpiresAt).Seconds())

	http.SetCookie(w, &http.Cookie{
		Name:     SessionCookieName,
		Value:    session.ID,
		Path:     "/",
		Domain:   d.Config.CookieDomain,
		HttpOnly: true,
		Secure:   secure,
		SameSite: http.SameSiteLaxMode,
		MaxAge:   maxAge,
	})
	// Readable by frontend JS on purpose — it's echoed back as the
	// X-CSRF-Token header, never trusted as an identity credential itself.
	// Domain defaults to "" (host-only cookie); set COOKIE_DOMAIN when the
	// frontend and backend are on different subdomains of the same parent
	// domain, or the frontend's JS can never read this cookie to echo it.
	http.SetCookie(w, &http.Cookie{
		Name:     CSRFCookieName,
		Value:    session.CSRFToken,
		Path:     "/",
		Domain:   d.Config.CookieDomain,
		HttpOnly: false,
		Secure:   secure,
		SameSite: http.SameSiteLaxMode,
		MaxAge:   maxAge,
	})

	http.Redirect(w, r, d.Config.FrontendURL+req.RedirectTo, http.StatusFound)
}

func (d *Deps) handleLogout(w http.ResponseWriter, r *http.Request, userID string) {
	if cookie, err := r.Cookie(SessionCookieName); err == nil {
		_ = d.AuthStore.DeleteSession(r.Context(), cookie.Value)
	}
	clearCookie(w, SessionCookieName, d.Config.CookieDomain)
	clearCookie(w, CSRFCookieName, d.Config.CookieDomain)
	respondNoContent(w)
}

func clearCookie(w http.ResponseWriter, name, domain string) {
	http.SetCookie(w, &http.Cookie{Name: name, Value: "", Path: "/", Domain: domain, MaxAge: -1})
}

func (d *Deps) handleMe(w http.ResponseWriter, r *http.Request, userID string) {
	if userID == "" {
		respondError(w, http.StatusUnauthorized, "UNAUTHENTICATED", "login required")
		return
	}
	user, err := d.Users.GetByID(r.Context(), userID)
	if err != nil {
		respondDomainError(w, apperr.ErrNotFound)
		return
	}
	respondJSON(w, http.StatusOK, toUserDTO(user))
}
