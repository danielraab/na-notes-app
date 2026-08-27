# 11. Per-implementation `.env.example` files, not one root file

## Status

Accepted. Supersedes [ADR 0010](0010-docker-compose-orchestration.md)'s
single-root-`.env` decision.

## Context

ADR 0010 put one root `.env.example` in front of the whole stack, on the
theory that it's the single easiest way to try the project
(`cp .env.example .env; docker compose up`) and that variable names are a
shared contract across backend implementations anyway.

Two problems surfaced once a second concern was raised in review:

- Variable *names* being a cross-implementation contract (they are,
  enforced by `internal/config` and ADR 0005's cookie/header names) is a
  different claim from variable *values* needing to live in one file. No
  variable in this project is actually consumed by both a backend and a
  frontend process at once — each var belongs to exactly one
  implementation. The root file's "shared" framing was really just
  convenience, not a technical requirement.
- As more backend/frontend implementations are added (the whole premise
  of ADR 0002's polyglot design), a single root file becomes a grab-bag of
  every implementation's variables, most irrelevant to whichever pair
  you're actually running. It also assumes every implementation can be
  configured with plain env vars of a fixed name — usually true (see
  ADR 0005's discussion of Quarkus-style config indirection), but the
  file's existence at the root implies a rigidity that isn't the real
  constraint.

## Decision

- Each implementation folder owns its own `.env.example` (`backend-go/.env.example`,
  `frontend-react/.env.example`, ...), documenting only the variables that
  implementation reads.
- The cross-implementation contract is still real, but it's about
  *names*, not file location: every backend implementation must accept
  the same variable names as `backend-go/.env.example` documents, so
  swapping `docker-compose.yml`'s `build.context` to a different backend
  needs no other change (per the root `README.md`'s implementation-swap
  instructions). Frontend implementations have no equivalent naming
  contract since only one frontend consumes each of its own build-time
  vars.
- `docker-compose.yml` loads each service's config via that service's own
  `env_file:` (e.g. `./backend-go/.env`) instead of interpolating
  `${VAR}` from a root `.env` into an `environment:` block. Build-time
  args (`VITE_API_BASE_URL`) can't be sourced via `env_file:` — Compose
  only interpolates `build.args` from the shell or a root `--env-file` —
  so the compose file's default there mirrors the frontend Dockerfile's
  own `ARG` default directly instead.
- The root `README.md` quickstart copies each folder's `.env.example`
  individually.

## Consequences

- Trying the default stack now takes two `cp` commands instead of one,
  in exchange for every implementation being self-contained and folder-scoped.
- A new implementation folder never touches another implementation's env
  file; it adds its own `.env.example` and, for a backend, follows the
  same variable-name contract documented there.
- Overriding `VITE_API_BASE_URL` for `docker compose up --build` means
  editing `docker-compose.yml` directly (or building `frontend-react`
  standalone with `--build-arg`), since Compose build-arg interpolation
  can't read a non-root `.env` file.
