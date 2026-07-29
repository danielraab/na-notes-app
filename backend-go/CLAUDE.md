# CLAUDE.md — backend-go

Read the repo root [`AGENTS.md`](../AGENTS.md) first — it applies here in
full. This file only adds Go-specific notes.

- Follow standard Go idiom: `gofmt`/`go vet` clean, errors wrapped with
  `%w` and context, no naked panics in request handling paths.
- Business rules belong in `internal/notes`/`internal/users` (the
  `service.go` files), not in `internal/httpapi` handlers — see
  [`docs/decisions/0003-package-layout.md`](docs/decisions/0003-package-layout.md).
- Never bypass `database/sql` parameterized queries for user-controlled
  input.
- Run `go build ./... && go vet ./... && go test ./...` before considering
  a change done.
- If a change touches `openapi/openapi.yaml` semantics (new field,
  endpoint, or behavior), update `internal/httpapi/dto.go` and the
  relevant handler together, and flag that other backend implementations
  need the equivalent change.
