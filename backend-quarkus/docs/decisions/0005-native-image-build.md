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
  (`quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`), so
  `./gradlew build -Dquarkus.package.type=native` builds the native
  executable inside a container rather than requiring a local GraalVM
  install — the same default trade-off `quarkus create app` makes.
- `Dockerfile.native` is a second, separate Dockerfile (the default
  `Dockerfile` stays JVM-mode). It is a two-stage build symmetric with the
  JVM `Dockerfile`: the first stage runs `./gradlew build` **inside the
  pinned Mandrel builder image**
  (`quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`) to produce
  the native `build/*-runner`, overriding
  `-Dquarkus.native.container-build=false` because native-image runs
  directly in that stage rather than in a nested container; the second
  stage packages the runner into
  `quay.io/quarkus/ubi9-quarkus-micro-image` — the **`ubi9-`** variant is
  mandatory because the `jdk-25` Mandrel builder is UBI9-based (glibc
  2.34) and the plain `quarkus-micro-image:2.0` is still UBI8 (glibc
  2.28); see Consequences. A single
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
- **The `Feature` does *not* register the driver with `DriverManager`.**
  A first real native run started, then failed at DB open with
  `java.sql.SQLException: No suitable driver found for jdbc:sqlite:...` —
  native-image strips the `ServiceLoader` lookup of
  `META-INF/services/java.sql.Driver` that `DriverManager` relies on, and
  nothing in this project (no `quarkus-jdbc-*` extension — it uses plain
  JDBC per ADR 0001) re-registers it. Fixed in `db/Database.kt` by
  calling `org.sqlite.JDBC.createConnection(url, props)` directly instead
  of `DriverManager.getConnection` — a static class reference that also
  keeps `org.sqlite.JDBC` reachable, and is identical behaviour on the
  JVM (JVM build + DB/repository tests still pass).
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
- **Runtime base image glibc must match the builder's.** A native build
  run against this `Dockerfile.native` produced a working
  `build/*-runner`, but the container failed at startup with
  `/lib64/libc.so.6: version `GLIBC_2.33' not found (required by
  /app/application)` — the `jdk-25` (Mandrel 25.0) builder is UBI9 and
  dynamically links glibc 2.34, while the previously-used
  `quay.io/quarkus/quarkus-micro-image:2.0` is UBI8 (glibc 2.28). Fixed
  by switching the runtime stage to
  `quay.io/quarkus/ubi9-quarkus-micro-image:2.0`. On any future Mandrel
  bump, keep the runtime base on the same UBI major as the builder.
- **Runtime stage now declares `VOLUME ["/data"]`**, matching the JVM
  `Dockerfile`, after a container hit
  `java.lang.IllegalStateException: failed to open database` at startup.
  `/data` was already created and `chmod 777`'d at build time, so a
  plain `docker run` with no extra flags was already writable — this
  fixes the case where the container is started with `--read-only` (or
  an orchestrator's `readOnlyRootFilesystem: true`), where only
  declared-volume paths stay writable. If `failed to open database`
  still reproduces on a container run *without* a read-only root
  filesystem, the volume declaration wasn't the actual cause and the
  sqlite-jdbc native-image path (see above) is the next thing to check —
  its native library extraction happens at `java.io.tmpdir`, not
  `/data`, and hasn't been individually verified here.
- **`/data` is created and `chown`ed as `USER root` in the runtime
  stage.** The `ubi9-quarkus-micro` base already ends on `USER 1001`, so
  the earlier `RUN mkdir -p /data && chmod 777 /data` executed
  unprivileged and produced a wrongly-owned directory. It is now
  `USER root` → `mkdir` → `chown 1001:0` → `chmod 775` → `USER 1001`,
  matching the JVM `Dockerfile`'s `chown app:app /data`. Note this only
  governs the directory baked into the image (and what Docker copies into
  a fresh *named* volume on first use); a host **bind mount** over
  `/data` keeps the host path's ownership and must be made writable for
  UID 1001 on the host side.
- The Mandrel builder-image tag is pinned in two places that must move
  together on every Quarkus upgrade: `%native.quarkus.native.builder-image`
  in `application.properties` and the `FROM ... AS build` line in
  `Dockerfile.native`. Quarkus enforces an exact Mandrel major it supports
  and fails the native build with "Out of date version of GraalVM or
  Mandrel detected" if the image lags — this happened on the 3.39.1 bump
  (`jdk-21` / Mandrel 23.1 → `jdk-25` / Mandrel 25.0).
- If nimbus-jose-jwt does need hand-written reflection config, it belongs
  in `src/main/resources/META-INF/native-image/` as a
  `reflect-config.json` (or via `@RegisterForReflection` on the specific
  classes GraalVum reports missing) — add it, and a note here, once a real
  native build has actually surfaced what's missing, rather than
  guessing at it speculatively now.
