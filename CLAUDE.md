# CLAUDE.md

This file's instructions apply repo-wide when Claude Code is working here.

**Read [`AGENTS.md`](AGENTS.md) first — it is the canonical set of working
rules for this repository** (how to work, the shared API contract, where
to document decisions, definition of done). Everything there applies to
you exactly as written.

Claude-Code-specific notes on top of that:

- When starting work in an implementation folder (`backend-*`/`frontend-*`),
  also read that folder's own `CLAUDE.md`/`README.md`/`docs/decisions/` —
  root `AGENTS.md` covers cross-cutting rules, not that stack's specifics.
- Prefer your `Read`/`Grep`/`Glob` tools over shelling out to `find`/`cat`/`grep`.
- Before reporting a change complete, actually build/run it (or say
  explicitly that you couldn't) — see "Before considering a change done"
  in `AGENTS.md`.
- If a task would require changing more than one implementation folder
  (e.g. an OpenAPI contract change), say so up front rather than silently
  only updating one.
