# Agent instructions for this repository

These instructions apply to every folder in this repo unless a more
specific `AGENTS.md`/`CLAUDE.md` inside an implementation folder overrides
something for that implementation. If a folder is asking for something
that seems to disagree with a rule here, follow the more specific one but
say so.

## What this repository is

A single product (a notes app) implemented multiple times in different
backend languages/frameworks and different frontend frameworks, to
compare them under an identical spec. Read `README.md` and everything in
`docs/adr/` before making changes — they explain *why* the constraints
below exist, not just that they exist.

## How to work

- **Act like a professional software developer working in a shared
  codebase**, not like you're producing a one-off script: clean, readable
  code; a clear, coherent architecture within whatever folder you're
  editing; consistent with the conventions already established in that
  folder.
- **Minimal, necessary changes.** Solve the task you were given. Don't
  refactor unrelated code, don't add abstractions for hypothetical future
  needs, don't "clean up while you're in there" unless asked. Three
  similar lines beat a premature abstraction.
- **Think before acting, and ask when something is genuinely ambiguous.**
  If a requirement is underspecified in a way that would change the
  design (not just a minor detail you can reasonably default), stop and
  ask rather than guessing. Don't ask about things you can resolve by
  reading the existing code/docs.
- **Security is not optional.** Validate input at trust boundaries, never
  trust client-supplied identifiers for authorization decisions, use
  parameterized queries always, never log secrets/tokens, use a CSPRNG for
  anything security-sensitive (tokens, session IDs). See the
  security-relevant ADRs (0004, 0005, 0006, 0009) — they are requirements,
  not suggestions.
- **Best practices for the language/framework you're in.** Follow that
  ecosystem's idioms (formatting, project layout, testing conventions),
  not a style imported from a different implementation folder.

## The contract that must never silently drift

- `openapi/openapi.yaml` is the one API contract every backend implements
  and every frontend consumes (ADR 0003). If you need a new field or
  endpoint, edit the spec first, then implement it — and if you're
  changing a backend, check whether other backends now need the same
  change to stay in sync. Don't invent undocumented endpoints/fields.
- Cookie names (`session`, `csrf_token`), the CSRF header
  (`X-CSRF-Token`), and CORS behavior must be identical across backends
  (ADR 0005) — a frontend must not need backend-specific logic.
- Never make a change to one implementation that quietly relies on
  behavior another implementation wouldn't provide.

## Documenting decisions

- **Cross-cutting decisions** (anything that affects more than one
  implementation folder, or the contract between them) go in
  `docs/adr/` at the repo root as a new numbered ADR. Don't edit past
  ADRs' decisions in place — add a new one that supersedes it.
- **Implementation-specific decisions** (library choices, internal folder
  layout, why this ORM/router/state-management library) go in that
  implementation's own `docs/decisions/` folder. Future contributors to
  that implementation should be able to read that folder and understand
  why it looks the way it does without asking the original author.
- Update the relevant `README.md` when you change how something is run,
  configured, or tested — don't let docs go stale.

## Before considering a change done

- The implementation still builds its Docker image.
- It still satisfies `openapi/openapi.yaml` (contract tests if the
  implementation has them; manual check otherwise).
- Existing tests pass; you've added tests for new behavior where the
  implementation has a test suite.
- You haven't touched a different implementation folder unless the task
  required a contract change that must be reflected everywhere.
