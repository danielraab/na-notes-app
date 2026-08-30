# 1. `axum` for HTTP, `tokio` as the async runtime

## Status

Accepted

## Context

Unlike Go's standard library (`net/http.ServeMux` in Go 1.22+ already has
method- and wildcard-aware routing, see `backend-go/docs/decisions/0001-standard-library-http-router.md`),
Rust's standard library has no HTTP server at all — an async runtime and
an HTTP layer are both third-party choices.

## Decision

Use `axum` on top of `tokio`. It's maintained by the Tokio project itself,
has no separate routing-macro/codegen step, and its `Router`/`State`/
extractor model maps cleanly onto the same request pipeline shape
backend-go uses by hand in `internal/httpapi/middleware.go`:

- Route registration (`Router::new().route(...)`) mirrors `mux.HandleFunc`.
- `axum::middleware::from_fn`/`from_fn_with_state` wrap the whole router,
  applied in the same outer-to-inner order backend-go's `NewRouter` uses
  (request logging → CORS → session lookup → CSRF → routes) — see
  `src/httpapi/server.rs`.
- Custom `FromRequestParts` extractors (`RequireAuth`, `OptionalAuth` in
  `src/httpapi/middleware.rs`) play the role of backend-go's
  `withAuth`/`withOptionalAuth` handler wrappers.

No cookie- or CORS-handling crate (e.g. `axum-extra`'s cookie jar,
`tower-http`'s CORS layer) is used — cookies and CORS are hand-rolled in
`src/httpapi/cookies.rs`/`middleware.rs`, the same way backend-go hand-rolls
them with `net/http.Cookie` rather than a framework helper, so the exact
header/attribute behavior required by ADR 0005 stays explicit and easy to
audit against that ADR rather than delegated to a general-purpose crate's
defaults.

## Consequences

- One more dependency than backend-go needs (`axum` + `tokio`), because
  Rust's standard library doesn't include an HTTP server; this is normal
  for the Rust ecosystem, not a deviation specific to this project.
- The middleware pipeline is explicit function-by-function, matching
  backend-go's style, rather than a declarative tower-http `ServiceBuilder`
  stack — easier to line up 1:1 against backend-go's
  `internal/httpapi/middleware.go` when comparing implementations.
- If routing needs grow past what `axum` offers directly, revisit this
  decision rather than reaching for a second framework alongside it.
