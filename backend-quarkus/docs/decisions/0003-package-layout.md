# 3. Package layout mirrors backend-go's package split

## Status

Accepted.

## Context

`backend-go`'s `docs/decisions/0003-package-layout.md` and its own
`CLAUDE.md` establish a rule this repo's contributors already rely on:
business rules live in the domain packages (`internal/notes`,
`internal/users`), not in the HTTP handlers. Comparing implementations
side by side is easier when the same split exists in every backend, even
though ADR 0002 (root) explicitly allows each implementation to choose its
own internal architecture.

## Decision

`backend-quarkus` uses the same split, translated to Java packages under
`app.nanotes.backend`:

```
config/       environment variable loading (AppConfig) + the LISTEN_ADDR
              bridge (ListenAddrConfigSource) — see application.properties
db/           the single JDBC connection + migrations (Database, Migrator)
              — see docs/decisions/0001-plain-jdbc-and-sqlite.md
auth/         OIDC client + session/PKCE-state storage
              — see docs/decisions/0002-manual-oidc-client.md
users/        user accounts (created lazily on first login)
notes/        notes domain: model records, NoteRepository, NoteService
              (business rules), Cursor (ADR 0007 pagination encoding)
mail/         notification emails, via quarkus-mailer
web/          JAX-RS resources, request/response DTOs, session/CSRF
              filters, exception-to-HTTP-status mapping — the only
              package that knows about HTTP
apperr/       sentinel domain exceptions, mapped to HTTP status in web/
randtoken/    CSPRNG token generation (sessions, CSRF, share links)
```

`notes.NoteService` (not `web.NotesResource`/`web.SharingResource`) owns
authorization decisions (owner/edit/read checks), validation, and
mention-notification logic — the JAX-RS resources only translate between
HTTP and the service layer, the same responsibility split
`backend-go/internal/httpapi` has relative to `internal/notes`.

## Consequences

- A contributor already familiar with `backend-go`'s layout can navigate
  this implementation by name alone.
- `notes`/`users`/`auth`/`randtoken` have no `jakarta.ws.rs`/HTTP
  dependency, so `NoteRepositoryTest`/`UserRepositoryTest`/`CursorTest`
  exercise them directly against a temp SQLite file with no web-framework
  bootstrap — mirroring `backend-go/internal/notes`'s repository-level
  tests (see the root `README.md`'s testing section for this
  implementation).
