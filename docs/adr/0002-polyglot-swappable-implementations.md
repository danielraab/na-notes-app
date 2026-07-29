# 2. Polyglot, swappable backends and frontends

## Status

Accepted

## Context

The purpose of this project is to try the same product in different
languages/frameworks, on both the backend and the frontend, and to be able
to mix and match ("frontend-react talking to backend-go today,
backend-rust tomorrow, no changes to the frontend").

## Decision

- Each backend implementation lives in its own top-level folder named
  `backend-<tech>` (e.g. `backend-go`). Each frontend implementation lives
  in `frontend-<tech>` (e.g. `frontend-react`). A combined implementation
  that isn't meaningfully split (rare) would live in `fullstack-<tech>`.
- Every `backend-*` folder implements the *same* contract: the OpenAPI
  spec in `/openapi/openapi.yaml`, the same cookie/CSRF/CORS behavior, and
  the same data semantics (sharing, mentions, pagination). It owns its own
  SQLite database and migrations.
- Every `frontend-*` folder implements the *same* product against that
  contract and must not depend on any backend-specific behavior that isn't
  in the OpenAPI spec.
- Any backend must work with any frontend without code changes on either
  side, as long as both implement the current contract version.
- A folder is free to choose its own internal architecture, libraries, and
  project layout, as long as it satisfies the shared contract and the
  non-functional requirements in `README.md` (Docker image, OIDC login,
  CORS/CSRF, SQLite-only DB access, etc).

## Consequences

- The OpenAPI spec becomes the single source of truth for
  frontend/backend compatibility; it must live outside any single
  implementation folder (see ADR 3).
- Adding a new backend or frontend is additive: create the folder, satisfy
  the contract, wire it into `docker-compose.yml` and CI. No existing
  implementation should need to change.
- Feature work that changes the contract (new endpoint, new field) must be
  proposed as an OpenAPI change first, then implemented in *every*
  existing backend, not just the one a contributor happens to be working
  in.
