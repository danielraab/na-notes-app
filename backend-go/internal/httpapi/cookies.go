package httpapi

// Cookie and header names are part of the cross-implementation contract
// (ADR 0005) — every backend must use these exact names so a frontend
// implementation never needs backend-specific logic.
const (
	SessionCookieName = "session"
	CSRFCookieName    = "csrf_token"
	CSRFHeaderName    = "X-CSRF-Token"
)
