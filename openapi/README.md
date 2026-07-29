# OpenAPI Contract

`openapi.yaml` is the single, authoritative REST contract for this
project (see [ADR 0003](../docs/adr/0003-global-openapi-contract.md)).
Every `backend-*` implementation must satisfy it exactly; every
`frontend-*` implementation must only rely on what it documents.

## Rules

1. **This file is the source of truth.** If a backend needs a field or
   endpoint that isn't here, add it here first, then implement it in
   every backend — not just the one you're working on.
2. **Versioning**: `info.version` follows SemVer. Backwards-compatible
   additions (new optional field, new endpoint) bump the minor version.
   Anything a existing client could break on bumps the major version.
3. **Cookies and CSRF are part of the contract, not an implementation
   detail** — see [ADR 0004](../docs/adr/0004-generic-oidc-httponly-cookie.md)
   and [ADR 0005](../docs/adr/0005-csrf-and-cors.md). The cookie names
   (`session`, `csrf_token`) and header name (`X-CSRF-Token`) must match
   across all backends.

## Validating a backend against the spec

Any implementation can be checked for drift with a generic OpenAPI
validator/contract-testing tool run against a live instance, for example:

```bash
npx @stoplight/spectral-cli lint openapi/openapi.yaml
```

Implementations are encouraged to add their own contract tests (e.g. using
[Schemathesis](https://schemathesis.readthedocs.io/) or
[Dredd](https://dredd.org/)) in their own test suite, pointed at this file
— see each backend's `README.md` for how it does this.

## Generating server/client code

Implementations may (but are not required to) generate server stubs or
client code from this file using [openapi-generator](https://openapi-generator.tech/)
or language-specific equivalents. Generated code is regenerated from this
file, never hand-edited — if generated code needs to change, change this
spec first.

## Local preview

```bash
npx @redocly/cli preview-docs openapi/openapi.yaml
```
