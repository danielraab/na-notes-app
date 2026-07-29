# 8. Optimistic concurrency for note edits

## Status

Accepted

## Context

A note can be shared as editable with other users, so two people can open
the same note and edit concurrently. Silently overwriting one editor's
changes (last-write-wins) causes silent data loss.

## Decision

- Every note has a monotonically increasing `version` integer, returned in
  every `GET`/`POST` response.
- `PUT /api/notes/{id}` requires the client to send the `version` it last
  read (`If-Match: <version>` header or `version` body field — fixed
  precisely in the OpenAPI spec). If the stored version no longer matches,
  the backend rejects the update with `409 Conflict` and returns the
  current server copy of the note.
- The frontend surfaces the conflict to the user (e.g. "this note changed
  since you loaded it — reload to see the latest version and reapply your
  edit") rather than silently discarding either side. Automatic
  three-way-merge is out of scope.

## Consequences

- No silent data loss on concurrent edits.
- Slightly more client-side handling than last-write-wins, but identical
  across all implementations since it's part of the shared contract.
