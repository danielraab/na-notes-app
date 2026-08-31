# CLAUDE.md — backend-quarkus

Read the repo root [`AGENTS.md`](../AGENTS.md) first — it applies here in
full. This file only adds Quarkus/Kotlin-specific notes.

- Follow standard Kotlin idiom for this codebase: `data class` for
  immutable model/DTO records, nullable types instead of `Optional<T>`
  (except at a genuine Java-interop boundary, e.g. MicroProfile Config's
  `@ConfigProperty` injection), no field injection — constructor injection
  only.
- Business rules belong in `notes.NoteService`/`users.UserRepository`, not
  in `web` JAX-RS resources — see
  [`docs/decisions/0003-package-layout.md`](docs/decisions/0003-package-layout.md).
- Never bypass `db.Database`'s parameterized `?` placeholders for
  user-controlled input.
- Run `./gradlew build` (compiles + tests + packages) before considering a
  change done. `./gradlew test` alone runs just the test suite.
- If a change touches `openapi/openapi.yaml` semantics (new field,
  endpoint, or behavior), update `web/dto/Dtos.kt` + `web/Dtos` and the
  relevant resource/service together, and flag that other backend
  implementations need the equivalent change.
- **A `Boolean` DTO property named `isXxx` needs
  `@get:JsonProperty("isXxx")`** — Kotlin compiles it to a getter literally
  named `isXxx()`, and Jackson's default bean-property naming then strips
  the `is` prefix as it would for a Java boolean getter, silently
  serializing the field under the wrong name (see
  [`docs/decisions/0004-kotlin-and-gradle.md`](docs/decisions/0004-kotlin-and-gradle.md)
  — this exact bug happened once already, to `NoteDto.isPublic`).
- A CDI bean with more than one constructor must mark exactly one
  `@Inject` (see `OidcClient`, `Database`) — otherwise Quarkus's build-time
  ArC validation fails with an "Unsatisfied dependency" error that doesn't
  obviously point at "add `@Inject`".
- Only `@ApplicationScoped`/`@RequestScoped` classes need to be open (see
  the `allOpen` block in `build.gradle.kts`) — don't add `@Path`/`@Provider`
  classes to it; they default to pseudo-scopes that don't need proxying,
  and adding them is more likely to introduce the "open var with a private
  setter" conflict (see `CurrentSession`'s accessor-function-based API)
  than to fix anything.
- Native-image support (`Dockerfile.native`) is configured but **not
  verified** in this environment — see
  [`docs/decisions/0005-native-image-build.md`](docs/decisions/0005-native-image-build.md).
  If you get a working GraalVM/Mandrel + Docker environment, actually
  running a native build and updating that ADR with what you found (rather
  than assuming it "just works") is more valuable than any other single
  follow-up in this folder.
