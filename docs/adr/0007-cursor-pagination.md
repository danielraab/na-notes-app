# 7. Cursor pagination for the notes dashboard

## Status

Accepted

## Context

The homepage dashboard lists notes (initially 12, "load more" on scroll).
Offset-based pagination shifts results when notes are created/deleted
between page loads, causing duplicates or skipped items during infinite
scroll.

## Decision

- The `GET /api/notes` endpoint is cursor-paginated: it accepts a `cursor`
  (opaque string, backend-defined encoding) and `limit` (default and
  initial page size `12`, max enforced server-side e.g. `50`) query
  parameter, and returns `items` plus a `nextCursor` (`null` when there are
  no more results).
- The cursor encodes a stable sort key (e.g. `updated_at` + `id` tiebreaker)
  so pagination is stable even as notes are edited.
- The frontend never constructs or interprets the cursor; it only passes
  back the `nextCursor` it was given.

## Consequences

- All backends must return an opaque cursor of this shape; frontends can
  be written once against it regardless of backend.
- Consistent, duplicate-free infinite scroll even under concurrent writes.
