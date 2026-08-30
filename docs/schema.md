# Reference schema

This is the **non-binding reference data model** for NA Notes, described
independently of any SQL dialect or ORM. See
[ADR 0014](adr/0014-reference-schema-for-cross-implementation-migration.md)
for why this exists and what it does (and doesn't) commit an
implementation to. It currently matches `backend-go`'s schema
(`backend-go/internal/db/migrations/0001_init.sql`) exactly — that's where
it was extracted from, not the other way around.

Types below are logical, not SQL types: `string` is unbounded text,
`id` is a string unique identifier (implementations have used UUIDs;
any string identifier works), `timestamp` is an ISO 8601 / RFC 3339
instant, `enum(...)` is a string constrained to the listed values.

## `users`

One row per person who has ever logged in (accounts are created lazily on
first successful OIDC login — there's no separate registration flow).

| Field | Type | Notes |
|---|---|---|
| `id` | `id`, PK | |
| `oidc_subject` | `string`, unique, not null | The OIDC provider's stable subject claim. Matching happens on this, never on email alone (a provider may allow email reuse/change). |
| `email` | `string`, unique, not null | Refreshed from the provider on every login. |
| `display_name` | `string`, not null | Refreshed from the provider on every login. |
| `avatar_url` | `string`, nullable | |
| `created_at` | `timestamp`, not null | |

## `notes`

| Field | Type | Notes |
|---|---|---|
| `id` | `id`, PK | |
| `owner_id` | `id`, not null, FK → `users.id`, cascade delete | |
| `title` | `string`, not null | |
| `content_markdown` | `string`, not null | Always the source of truth; rich text (if an implementation offers it) must serialize to/from this. |
| `version` | `integer`, not null, default `1` | Incremented on every update; used for optimistic-concurrency conflict detection ([ADR 0008](adr/0008-optimistic-concurrency-for-notes.md)). |
| `created_at` | `timestamp`, not null | |
| `updated_at` | `timestamp`, not null | Drives dashboard ordering and the pagination cursor ([ADR 0007](adr/0007-cursor-pagination.md)). |

Indexed by `(owner_id, updated_at desc, id desc)` — the `id` tiebreaker
matters: `updated_at` alone isn't unique enough for a stable cursor.

## `note_shares`

Explicit per-user grants on a note (in addition to the owner, who always
has full access).

| Field | Type | Notes |
|---|---|---|
| `note_id` | `id`, not null, FK → `notes.id`, cascade delete | Part of composite PK `(note_id, user_id)`. |
| `user_id` | `id`, not null, FK → `users.id`, cascade delete | Part of composite PK. |
| `permission` | `enum(read, edit)`, not null | |
| `created_at` | `timestamp`, not null | |

Indexed by `user_id` (for "notes shared with me" lookups).

## `note_public_shares`

At most one public link per note.

| Field | Type | Notes |
|---|---|---|
| `note_id` | `id`, PK, FK → `notes.id`, cascade delete | One row per publicly-shared note. |
| `token` | `string`, unique, not null | Unguessable random token, CSPRNG-generated ([ADR 0009](adr/0009-public-share-random-token.md)) — never a sequential or derivable ID. |
| `created_at` | `timestamp`, not null | Regenerated (row replaced) each time the link is re-published. |

## `note_mentions`

Tracks which users have already been notified of an @-mention in a note,
so re-saving the same note doesn't re-notify them.

| Field | Type | Notes |
|---|---|---|
| `note_id` | `id`, not null, FK → `notes.id`, cascade delete | Part of composite PK `(note_id, user_id)`. |
| `user_id` | `id`, not null, FK → `users.id`, cascade delete | Part of composite PK. |
| `created_at` | `timestamp`, not null | |

## `sessions`

Server-side session store backing the HttpOnly session cookie
([ADR 0004](adr/0004-generic-oidc-httponly-cookie.md)).

| Field | Type | Notes |
|---|---|---|
| `id` | `id`, PK | The opaque value set as the `session` cookie. CSPRNG-generated, never guessable. |
| `user_id` | `id`, not null, FK → `users.id`, cascade delete | |
| `csrf_token` | `string`, not null | Paired with the `csrf_token` cookie for double-submit CSRF protection ([ADR 0005](adr/0005-csrf-and-cors.md)). |
| `expires_at` | `timestamp`, not null | |
| `created_at` | `timestamp`, not null | |

Indexed by `user_id`.

## `oidc_requests`

Short-lived, server-side record of an in-flight OIDC login (authorization
code + PKCE), keyed by the OAuth `state`. Rows outlive their `expires_at`
only until the next login attempt opportunistically sweeps them.

| Field | Type | Notes |
|---|---|---|
| `state` | `string`, PK | The OAuth `state` parameter. |
| `code_verifier` | `string`, not null | PKCE verifier — must never be exposed to the browser, hence server-side storage rather than a client-readable cookie. |
| `redirect_to` | `string`, not null | Where to send the user after a successful login. |
| `expires_at` | `timestamp`, not null | |

## What's deliberately not pinned down here

- **Storage of `timestamp`/`enum`/`id` values** (SQL type, encoding,
  timezone representation) — an implementation's own choice, see its
  `docs/decisions/`.
- **Migration bookkeeping** (e.g. `backend-go`'s `schema_migrations`
  table) — infrastructure for that implementation's own migration runner,
  not part of the domain data.
- **Indexes beyond the ones called out above** — those exist to satisfy a
  specific documented requirement (cursor pagination, share lookups);
  implementations may add more for their own performance needs.
