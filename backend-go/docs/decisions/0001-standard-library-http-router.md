# 1. Use net/http's built-in router, not a third-party one

## Status

Accepted

## Context

Go 1.22+ added method- and wildcard-aware routing to `net/http.ServeMux`
(`mux.HandleFunc("GET /notes/{id}", ...)`, `r.PathValue("id")`), which
covers everything this API needs: fixed paths, path parameters, per-method
dispatch.

## Decision

Use the standard library `net/http.ServeMux` directly. No `chi`, `gin`,
`echo`, or similar.

## Consequences

- Zero routing dependencies; one less library to track for security
  updates.
- No middleware-chaining helper from a framework — `internal/httpapi/middleware.go`
  wraps `http.Handler` by hand, which is a handful of lines and keeps the
  request pipeline explicit (CORS → session lookup → CSRF → mux).
- If routing needs grow past what the standard library offers (e.g.
  regex constraints on path segments), revisit this decision rather than
  bolting a framework's router in halfway.
