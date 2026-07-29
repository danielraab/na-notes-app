# 9. Public note sharing uses an unguessable random token

## Status

Accepted

## Context

A note can be shared publicly as read-only, accessible via a link without
authentication. If the link were derived from a sequential/guessable ID,
anyone could enumerate and read other users' notes.

## Decision

- Publishing a note publicly creates a share record with a separate,
  cryptographically random token (>= 128 bits of entropy, URL-safe
  encoded), decoupled from the note's own primary key.
- The public view endpoint is `GET /api/public/notes/{token}` — knowledge
  of the note's internal ID must not be sufficient to read it; only the
  token is.
- Revoking public sharing invalidates the token (a new share creates a new
  token; there is no way to "guess forward" from an old, revoked token to
  a new one).
- Public access is always read-only, regardless of what access level the
  owner has. Editable access is only ever granted to specific
  authenticated users, never via the public link.

## Consequences

- Public links are safe to share in chat/email without exposing other
  notes.
- Backends must use a CSPRNG for token generation (never `math/rand` or
  equivalents) — called out explicitly in each backend's security notes.
