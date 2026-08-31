# CLAUDE.md — backend-quarkus

Read the repo root [`AGENTS.md`](../AGENTS.md) first — it applies here in
full. This file only adds Quarkus/Java-specific notes.

- Follow standard Java idiom for this codebase: records for immutable data,
  no checked-exception ceremony in request-handling paths (sentinel
  exceptions in `apperr/`, mapped to HTTP status by `web/*ExceptionMapper`
  classes), no field injection — constructor injection only.
- Business rules belong in `notes.NoteService`/`users.UserRepository`, not
  in `web` JAX-RS resources — see
  [`docs/decisions/0003-package-layout.md`](docs/decisions/0003-package-layout.md).
- Never bypass `db.Database`'s parameterized `?` placeholders for
  user-controlled input.
- Run `mvn -q clean package` (build + tests) before considering a change
  done. `mvn test` alone runs just the test suite.
- If a change touches `openapi/openapi.yaml` semantics (new field,
  endpoint, or behavior), update `web/dto/*` + `web/Dtos` and the relevant
  resource/service together, and flag that other backend implementations
  need the equivalent change.
- A CDI bean with more than one constructor must mark exactly one
  `@Inject` (see `OidcClient`, `Database`) — otherwise Quarkus's build-time
  ArC validation fails with an "Unsatisfied dependency" error that doesn't
  obviously point at "add `@Inject`".
