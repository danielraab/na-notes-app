# 5. Native-image build, opt-in via a second Dockerfile

## Status

Accepted, with one explicitly open risk (see Consequences) — this ADR
documents what's wired up and, just as importantly, what has **not** been
verified end-to-end.

## Context

Quarkus's headline resource-saving story is GraalVM/Mandrel native-image
compilation: a few tens of MB RSS and single-digit-millisecond startup,
versus the JVM mode this implementation runs by default (`Dockerfile`,
~150–250MB RSS, ~2s startup). That gap is the actual "resource saving"
people mean when they say "the Quarkus setup" — JVM mode alone doesn't
deliver it. A later request asked for native-image support specifically.

The sandbox this implementation was built and tested in has neither a
working Docker daemon (confirmed: `dockerd` fails to start even with the
sandbox's own restrictions lifted) nor a local GraalVM/Mandrel
installation. Native-image compilation — either locally or via
`-Dquarkus.native.container-build=true`, which needs Docker to run the
Mandrel builder image — could not be executed here. Everything below is
configured to the best of the available evidence (reading the actual
dependency jars' bundled GraalVM metadata, matching Quarkus's own
documented/generated conventions closely) but **not run**.

## Decision

- `application.properties` sets `%native.quarkus.native.container-build=true`
  and a pinned Mandrel builder image
  (`quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21`), so
  `./gradlew build -Dquarkus.package.type=native` builds the native
  executable inside a container rather than requiring a local GraalVM
  install — the same default trade-off `quarkus create app` makes.
- `Dockerfile.native` is a second, separate Dockerfile (the default
  `Dockerfile` stays JVM-mode). It is a two-stage build symmetric with the
  JVM `Dockerfile`: the first stage runs `./gradlew build` **inside the
  pinned Mandrel builder image**
  (`quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21`) to produce
  the native `build/*-runner`, overriding
  `-Dquarkus.native.container-build=false` because native-image runs
  directly in that stage rather than in a nested container; the second
  stage packages the runner into `quay.io/quarkus/quarkus-micro-image`,
  following Quarkus's own generated native-Dockerfile template. A single
  `docker build -f Dockerfile.native .` now does the whole thing — earlier
  revisions of this file only *packaged* an already-built runner and
  required a separate `./gradlew build -Dquarkus.package.type=native` step
  first (which also tripped over `.dockerignore` excluding `build/`).
- **`org.xerial:sqlite-jdbc` (ADR 0001) ships its own GraalVM `Feature`**
  (`org.sqlite.nativeimage.SqliteJdbcFeature`, registered via a
  `META-INF/native-image/org.xerial/sqlite-jdbc/native-image.properties`
  file bundled in the jar itself, auto-discovered by native-image with no
  configuration needed on this project's part) specifically to handle
  extracting and loading its native JNI library under native-image — this
  was the single biggest expected risk going in (JNI + native-image is
  usually a hard combination) and turned out to already be solved upstream
  as of sqlite-jdbc 3.5x.
- **`com.nimbusds:nimbus-jose-jwt` has no bundled native-image metadata of
  its own.** It's used elsewhere in the Quarkus ecosystem (transitively by
  `quarkus-oidc`/`smallrye-jwt`), so Quarkus's own native build tooling may
  already pull in reachability metadata for it from the community
  [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata) —
  but this project uses it directly (ADR 0002), not through either of
  those extensions, and that path was never exercised here. If a native
  build fails on missing-reflection-config for JOSE/JWT algorithm or key
  classes, this is the first place to look.

## Consequences

- JVM mode (`Dockerfile`) remains the default and the only one actually
  verified to work in this repository's CI and in this development
  environment — see the root `README.md`/CI job, which builds and tests
  in JVM mode only.
- Native-image support is present and configured correctly by every check
  available short of actually running `native-image` (dependency jar
  inspection, matching Quarkus's own generated templates, config that
  parses and applies without error in a JVM-mode build) — but is
  **unverified**. Before relying on it (e.g. wiring a native CI job or a
  production deployment), actually run
  `./gradlew build -Dquarkus.package.type=native` somewhere with a working
  Docker daemon or local GraalVM/Mandrel, and treat any reflection-related
  failure in the nimbus-jose-jwt code path as expected-possible rather
  than surprising.
- If nimbus-jose-jwt does need hand-written reflection config, it belongs
  in `src/main/resources/META-INF/native-image/` as a
  `reflect-config.json` (or via `@RegisterForReflection` on the specific
  classes GraalVum reports missing) — add it, and a note here, once a real
  native build has actually surfaced what's missing, rather than
  guessing at it speculatively now.
