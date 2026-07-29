# 10. Root docker-compose wires one backend + one frontend

## Status

Accepted

## Context

Every backend and frontend builds its own Docker image (a hard
requirement), but a developer trying the project needs an easy way to run
one concrete backend+frontend pair together, with a database volume and
shared configuration (OIDC, SMTP, CORS origins).

## Decision

- A root-level `docker-compose.yml` defines services for the *current*
  default pair (`backend-go`, `frontend-react`) plus a named volume for
  the SQLite file.
- Swapping implementations is done by pointing the `backend`/`frontend`
  service's `build.context` at a different folder (documented in the root
  `README.md`), not by maintaining a combinatorial matrix of compose
  files. As more implementations are added, the README's swap table is
  extended, not the compose file itself.
- All cross-cutting configuration (OIDC issuer/client, SMTP, allowed CORS
  origin, session secret) is passed via a single root `.env` file
  (`.env.example` committed, `.env` gitignored), consumed identically by
  every backend so swapping backends doesn't mean re-deriving config.

## Consequences

- `docker compose up` is the one-command way to try the project.
- Adding a new backend/frontend only requires adding a Dockerfile in its
  folder and one line of documentation on how to point compose at it —
  not a new compose file per combination.
