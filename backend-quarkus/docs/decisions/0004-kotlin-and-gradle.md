# 4. Kotlin + Gradle, not Java + Maven

## Status

Accepted. Supersedes this implementation's original Java + Maven form (the
domain logic, HTTP layer, and behavior are unchanged — this was a language
and build-tool rewrite, not a redesign).

## Context

`backend-quarkus` started as Java + Maven. Quarkus supports Kotlin as a
first-class language and Gradle as a first-class build tool equally well,
and a later request asked for both: Kotlin instead of Java, Gradle instead
of Maven, on top of adding native-image support (ADR 0005).

## Decision

- **Kotlin** (`kotlin("jvm")` + `kotlin("plugin.allopen")`, JVM target 21).
  Domain records became `data class`; `Optional<T>` return types inside the
  app (never at a genuine Java-interop boundary) became nullable `T?`;
  sentinel exceptions and CDI beans translate close to 1:1 from the Java
  version — see `docs/decisions/0003-package-layout.md`, still accurate.
- **The `allOpen` compiler plugin is required, not optional**: Quarkus's
  CDI container (ArC) generates subclass proxies for normal-scoped beans
  (`@ApplicationScoped`/`@RequestScoped`), which needs the bean class (and
  the members the proxy overrides) to be non-final — true of every Java
  class by default, but Kotlin classes are `final` unless opened.
  `build.gradle.kts` opens exactly `@ApplicationScoped` and
  `@RequestScoped` — not `@Path`/`@Provider`, which default to
  `@Dependent`/`@Singleton` pseudo-scopes in Quarkus REST that don't need
  proxying, so JAX-RS resources and exception mappers stay plain final
  Kotlin classes.
- **A CDI bean with more than one constructor needs an explicit
  `@Inject`** on the one CDI should use (see `Database`, `OidcClient`,
  which each also expose a private/test-only secondary constructor) — this
  was true in Java too, just less visible since Java doesn't make you
  choose a "primary" constructor.
- **`var` properties with a `private set` can't be `open`** — Kotlin
  rejects "open var, private setter" outright, and `@ApplicationScoped`/
  `@RequestScoped` beans are opened wholesale by the plugin above. Where
  the Java version had a `private set`-style field (`CurrentSession`), the
  Kotlin version exposes private backing fields through plain accessor
  functions instead of public `var` properties, sidestepping the conflict
  entirely rather than loosening the setter's visibility.
- **`Cursor` (ADR 0007) dropped its private-`ObjectMapper`-based JSON
  encoding** in favor of two base64url segments joined by `.`. The
  original approach would have needed `jackson-module-kotlin` registered
  on that specific `ObjectMapper` instance (not just the CDI-managed one
  `quarkus-kotlin` wires up automatically) to construct the private
  payload data class — simpler to sidestep than to wire up, and the
  cursor's encoding is a backend-internal implementation detail either way
  (ADR 0007: "opaque string, backend-defined encoding").
- **`isPublic: Boolean` needs an explicit `@get:JsonProperty("isPublic")`**
  on `NoteDto`/`NoteSummaryDto`. Kotlin compiles a property named
  `isPublic` to a getter method literally named `isPublic()`; Jackson's
  default bean-introspection then strips the leading `is` the way it would
  for a Java `boolean` getter, serializing the field as `"public"` instead
  of `"isPublic"` — silently breaking the `openapi.yaml` contract. Found by
  actually running the app and diffing a response against the Java
  version's, not by inspection — a reminder that this exact bug is easy to
  miss by code review alone.
- **Gradle** (Kotlin DSL — `build.gradle.kts`/`settings.gradle.kts`), with
  the standard wrapper (`./gradlew`) committed, matching every other
  Gradle project's convention (and this repo's own expectation that each
  implementation is runnable without additional global tool installs
  beyond its language's own toolchain).
  - `kotlin-stdlib`/`kotlin-reflect`, left unpinned, resolve to whatever
    version `enforcedPlatform(quarkus-bom)` happens to enforce — which can
    be newer than the Kotlin compiler plugin actually in use, producing
    "compiled with an incompatible version of Kotlin" errors. Pinned via a
    `configurations.all { resolutionStrategy.eachDependency { ... } }`
    block that forces the whole `org.jetbrains.kotlin` group to this
    project's Kotlin version.

## Consequences

- Functionally identical to the Java version — same contract, same tests
  (26, now in Kotlin under `src/test/kotlin`), same runtime behavior;
  verified by running the built jar and re-driving the same end-to-end
  flow (login redirect, note CRUD incl. the 409 conflict body, sharing,
  public share, logout) used to validate the original Java version.
- One behavior actually improved in the rewrite, not just translated: the
  `isPublic` bug above was caught and fixed *because* of the rewrite's
  re-verification pass, not despite it.
- `mvn`/`pom.xml` are gone; `./gradlew build` is the one command that
  compiles, packages, and tests this implementation, mirroring
  `backend-go`'s `go build && go test` and `backend-rust`'s `cargo build
  && cargo test` in spirit.
