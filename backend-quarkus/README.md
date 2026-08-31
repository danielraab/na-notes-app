# backend-quarkus

Quarkus (Kotlin) implementation of the NA Notes backend. Implements
[`/openapi/openapi.yaml`](../openapi/openapi.yaml) exactly — see the root
[`README.md`](../README.md) and [`/docs/adr`](../docs/adr) for the
cross-cutting rules this implementation follows (auth, CSRF/CORS,
pagination, sharing, concurrency).

## Stack

- Kotlin (JVM target 21), [Quarkus](https://quarkus.io) 3.39, Gradle
  (Kotlin DSL, wrapper committed — `./gradlew`).
- `quarkus-rest` + `quarkus-rest-jackson` + `quarkus-kotlin` — JAX-RS
  resources, Jackson JSON mapping with Kotlin data-class support.
- `org.xerial:sqlite-jdbc` — plain JDBC over SQLite, no ORM. This is the
  only supported database engine (see
  [`docs/decisions/0001-plain-jdbc-and-sqlite.md`](docs/decisions/0001-plain-jdbc-and-sqlite.md)).
- `com.nimbusds:nimbus-jose-jwt` for ID token signature verification, with
  a hand-rolled OIDC authorization-code+PKCE client (plain
  `java.net.http.HttpClient` for discovery/token exchange) — see
  [`docs/decisions/0002-manual-oidc-client.md`](docs/decisions/0002-manual-oidc-client.md)
  for why this isn't the `quarkus-oidc` extension.
- `quarkus-mailer` for notification emails.

See [`docs/decisions/`](docs/decisions) for the reasoning behind these
choices, the internal package layout
([`0003-package-layout.md`](docs/decisions/0003-package-layout.md)), the
Kotlin/Gradle rewrite from this implementation's original Java/Maven form
([`0004-kotlin-and-gradle.md`](docs/decisions/0004-kotlin-and-gradle.md)),
and native-image support
([`0005-native-image-build.md`](docs/decisions/0005-native-image-build.md)).

## Running locally (without Docker)

```bash
cd backend-quarkus
cp .env.example .env   # fill in your OIDC provider (issuer/client id/secret)
set -a
source .env
set +a
export DATABASE_URL=./notes.db
./gradlew quarkusDev
```

The server listens on `LISTEN_ADDR` (default `:8080`, bridged onto
Quarkus's own `quarkus.http.port` — see
[`ListenAddrConfigSource`](src/main/kotlin/app/nanotes/backend/config/ListenAddrConfigSource.kt)).
Migrations run automatically on startup.

`./gradlew quarkusDev` starts Quarkus dev mode (live reload); for a plain
run without dev-mode tooling, use `./gradlew quarkusRun` or build and run
the jar directly (see Docker section below).

## Configuration

All configuration is environment variables — see
[`.env.example`](.env.example) in this folder for the full list. Every
backend implementation in this repo must accept these exact variable
names (see [ADR 0011](../docs/adr/0011-per-implementation-env-files.md)),
so swapping backends via `docker-compose.yml`'s `build.context` doesn't
mean re-deriving config. Required:
`SESSION_SECRET`, `OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`,
`OIDC_CLIENT_SECRET`, `OIDC_REDIRECT_URL` — the process fails fast at
startup if any are missing. `COOKIE_DOMAIN` is optional and only needed
when the frontend and backend are deployed on different subdomains of the
same parent domain — without it, the CSRF double-submit cookie is a
frontend-unreadable host-only cookie on the backend's hostname and every
state-changing request fails with `CSRF_REJECTED`.

`DATABASE_URL` accepts a bare path, `sqlite://<path>`, or `file:<path>`
(default `./notes.db` if unset); a `postgres://`/`postgresql://` value is
rejected at startup with an explicit error — this implementation only
supports SQLite (ADR 0013 makes that opt-in, see
[`docs/decisions/0001-plain-jdbc-and-sqlite.md`](docs/decisions/0001-plain-jdbc-and-sqlite.md)).

## Project layout

```
src/main/kotlin/app/nanotes/backend/
  config/       environment variable loading
  db/           the single JDBC connection + migrations
  auth/         OIDC client + session/PKCE-state storage
  users/        user accounts (created lazily on first login)
  notes/        notes domain: model, repository, service (business rules)
  mail/         notification emails
  web/          JAX-RS resources, DTOs, session/CSRF filters
  apperr/       sentinel domain exceptions, mapped to HTTP status in web/
  randtoken/    CSPRNG token generation (sessions, CSRF, share links)
src/main/resources/
  application.properties     Quarkus config (CORS, mailer, native, ...)
  db/migrations/              forward-only SQL migrations + index.txt
```

`web` is the only package that knows about HTTP; `notes`/`users`/`auth`
have no `jakarta.ws.rs` dependency, so they're straightforward to unit
test directly against a temp SQLite file (see
`src/test/kotlin/**/*RepositoryTest.kt`).

## Testing

```bash
./gradlew test
```

`notes`/`users` have repository-level tests that exercise real SQLite (a
temp file per test, via `@TempDir`) — sharing visibility,
optimistic-concurrency conflicts, public share tokens, mention tracking,
and cursor pagination correctness under interleaved pages. `CursorTest`
and `MigratorSplitStatementsTest` cover the cursor encoding and the
migration-file statement splitter directly.

## Docker

### JVM mode (default)

```bash
docker build -t na-notes-backend-quarkus .
docker run --rm -p 8080:8080 --env-file .env -v notes-data:/data na-notes-backend-quarkus
```

The image is a multi-stage build: `./gradlew build` produces a Quarkus
fast-jar (`build/quarkus-app/`), copied into a plain
`eclipse-temurin:21-jre-jammy` image and run as a non-root user.
`/healthz` is used for the container `HEALTHCHECK`.

### Native image (opt-in, unverified — see ADR 0005)

```bash
./gradlew build -Dquarkus.package.type=native   # needs Docker (container-build) or local GraalVM/Mandrel
docker build -f Dockerfile.native -t na-notes-backend-quarkus-native .
docker run --rm -p 8080:8080 --env-file .env -v notes-data:/data na-notes-backend-quarkus-native
```

Trades JVM-mode's ~2s startup / ~150–250MB RSS for GraalVM/Mandrel's
single-digit-millisecond startup and tens-of-MB RSS — this is what people
usually mean by "the resource-saving Quarkus setup." **Configured but not
run end-to-end in this repository's development environment** (no working
Docker daemon or local GraalVM here) — read
[`docs/decisions/0005-native-image-build.md`](docs/decisions/0005-native-image-build.md)
before relying on it; `com.nimbusds:nimbus-jose-jwt`'s native-image
reflection needs in particular haven't been proven.

## Security notes specific to this implementation

- Session IDs, CSRF tokens, OIDC `state`, PKCE `code_verifier`, and public
  share tokens are all generated via `app.nanotes.backend.randtoken.RandToken`
  (`java.security.SecureRandom`). Never use `kotlin.random.Random` for any
  of these.
- Every `SELECT`/`INSERT`/`UPDATE`/`DELETE` goes through
  `app.nanotes.backend.db.Database`'s parameterized `?` placeholders —
  never string-concatenate user input into SQL.
- CSRF token comparison uses a constant-time comparison
  (`java.security.MessageDigest.isEqual`), not `String ==`.
- The public note endpoint (`GET /api/public/notes/{token}`) intentionally
  omits owner identity and note ID from its response — see
  [ADR 0009](../docs/adr/0009-public-share-random-token.md).
