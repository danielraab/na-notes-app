# 3. Global OpenAPI contract lives outside backend folders

## Status

Accepted

## Context

Multiple backends must expose an identical REST API so any frontend can
talk to any backend. If each backend owned its own API spec, the specs
would drift and "the contract" would stop being a single source of truth.

## Decision

The OpenAPI 3.1 document lives at `/openapi/openapi.yaml`, at the repo
root, outside every `backend-*`/`frontend-*` folder. It is:

- The only place request/response shapes, error format, auth scheme, and
  endpoints are defined.
- Versioned via the `info.version` field using SemVer. A breaking change
  bumps the major version and requires updating every backend
  implementation in the same change (or explicitly tracking which
  backends still support which version, if we ever need to support more
  than one version at once — not needed today).
- The reference each backend's tests validate against (contract tests:
  generate/validate requests and responses against the schema) and each
  frontend's API client is generated or hand-written from.

## Consequences

- Backend implementations must not add undocumented endpoints/fields that
  frontends come to rely on; extend the spec first.
- A single diff reviewer can see the whole API surface without opening any
  implementation folder.
- Tooling (linting the spec, generating clients/servers) is centralized in
  `/openapi/README.md` rather than duplicated per implementation.
