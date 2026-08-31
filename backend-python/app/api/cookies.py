"""Cookie and header names are part of the cross-implementation contract
(ADR 0005) — every backend must use these exact names so a frontend
implementation never needs backend-specific logic.
"""

SESSION_COOKIE_NAME = "session"
CSRF_COOKIE_NAME = "csrf_token"
CSRF_HEADER_NAME = "X-CSRF-Token"
