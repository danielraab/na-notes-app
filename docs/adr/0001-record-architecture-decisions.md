# 1. Record architecture decisions

## Status

Accepted

## Context

This repository hosts multiple interchangeable backend and frontend
implementations of the same product, used to compare languages and
frameworks. Decisions that apply across all implementations (protocols,
security model, data model constraints) must be written down once, in one
place, so that a new implementation doesn't quietly drift from an existing
one, and so a future contributor understands *why* a constraint exists
before "fixing" it away.

## Decision

We use lightweight Architecture Decision Records (ADRs) in `docs/adr/`,
numbered sequentially, one decision per file, using the format:
Status / Context / Decision / Consequences.

Cross-cutting decisions (auth model, pagination style, API contract, etc.)
go here. Decisions specific to a single implementation (e.g. "why this Go
web framework") go in that implementation's own `docs/decisions/` folder
instead — see `AGENTS.md` for the split.

## Consequences

- Every implementation must be consistent with the ADRs in this folder.
- A change that contradicts an existing ADR requires a new ADR that
  supersedes it, not a silent divergence in one implementation.
