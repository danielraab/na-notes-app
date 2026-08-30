# 14. A non-binding reference schema, not a tested cross-implementation contract

## Status

Accepted.

## Context

ADR 0006 deliberately lets each backend implementation own and model its
schema however it likes ("free to model the same concepts differently
internally"), and ADR 0013 lets a backend additionally choose which
database engine to run against. That freedom is the point of this repo
(ADR 0002) — but it also means someone wanting to move their data from one
implementation to another (or from SQLite to PostgreSQL on a different
implementation than the one they started with) has nothing to work from
except reading that implementation's source.

Two ways of closing that gap were considered and rejected:

- **One canonical migrations folder every backend applies verbatim.**
  Rejected: it would force every implementation onto the same
  literal SQL, defeating the point of letting each one use whatever
  migration tooling/ORM is idiomatic for its language — exactly the
  "wrapper library" friction this repo exists to let each implementation
  route around.
- **An automated test that introspects every backend's real schema and
  diffs it against a golden schema (optionally across engines, e.g.
  SQLite vs. PostgreSQL for the same backend).** Rejected: this fights
  the same freedom, and building a schema-introspection harness generic
  enough for arbitrary languages/migration frameworks/ORMs is a
  disproportionate amount of test infrastructure for a testbed project.
  No such test exists, and none is planned.

## Decision

- [`docs/schema.md`](../schema.md) documents a **reference schema**: the
  entities, fields, relationships, and semantic invariants (e.g. "token
  must be CSPRNG-generated", "id is part of a composite PK") that make up
  the NA Notes data model, described independently of any SQL dialect.
  It's derived from `backend-go`'s current schema.
- New backend implementations **should** model their schema close to this
  reference — same entities, fields, and semantics — so that migrating
  data between implementations (or writing a one-off export/import script)
  stays a tractable, mostly-mechanical mapping instead of a
  reverse-engineering exercise.
- This is guidance, not a contract: nothing tests or enforces it, and an
  implementation may still deviate where its language/framework's idioms
  call for it (per ADR 0006's original freedom). An implementation that
  deviates should say why in its own `docs/decisions/`, the same way
  `backend-go/docs/decisions/0005-postgres-support-via-pgx.md` documents
  its own schema-adjacent choices.
- `docs/schema.md` is a snapshot maintained by hand. When a migration
  changes `backend-go`'s schema in a way that changes the reference data
  model (not just SQL-dialect-level detail), update `docs/schema.md` in
  the same change — there's no automated check that will catch drift.

## Consequences

- Adding a new backend implementation is easier: `docs/schema.md` is a
  checklist for its first migration, instead of reading `backend-go`'s
  Go source to reverse-engineer the schema.
- No CI cost, no schema-introspection tooling to build or maintain.
- Drift between `docs/schema.md` and reality is possible and won't be
  caught automatically — it relies on whoever changes the schema
  remembering to update the doc, same as any other hand-maintained doc in
  this repo (e.g. README "how to run it" sections).
