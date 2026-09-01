# 15. CI runs only the implementations that changed

## Status

Accepted.

## Context

`.github/workflows/ci.yml` has one independent job per implementation
(ADR 0002), plus `openapi-lint` and the push-only
`backend-quarkus-native` job. Until now every job ran on every push and
every pull request, regardless of which folder the change touched. With
four backends, four frontends, and Docker-image builds in each, a
one-line change to `frontend-vue` still spent CI minutes rebuilding and
testing every other implementation.

`backend-quarkus-native` already special-cased this with a hand-rolled
`git diff --name-only` step, because native-image compilation is too
expensive to run unconditionally. The same reasoning applies to the rest
of the matrix, just less acutely.

## Decision

- A `changes` job runs first and uses `dorny/paths-filter@v3` to produce
  one boolean output per implementation folder (`backend-go`,
  `frontend-react`, …) plus `openapi`.
- Every implementation job gains `needs: changes` and
  `if: needs.changes.outputs.<folder> == 'true'`, so it runs only when
  its own `backend-*` / `frontend-*` folder changed in the push or PR.
- `openapi-lint` runs only when `openapi/**` changed.
- `backend-quarkus-native` keeps its `github.event_name == 'push'` guard
  and now takes its "touched backend-quarkus/" signal from the `changes`
  job instead of its own `git diff` step.
- Filtering is **per folder, with one shared exception: this workflow
  file**. Every filter also matches `.github/workflows/ci.yml` (via a
  `*pipeline` YAML anchor), so a change to the pipeline itself re-runs —
  and, on `main`, re-publishes (ADR 0016) — every implementation. A
  pipeline change therefore can't ship having been tested against only
  the one implementation that happened to change alongside it.
- A change to `openapi/**` still does *not* re-run the backend/frontend
  jobs. The contract stays enforced by each implementation's own contract
  tests when that implementation next changes, and by review.

## Consequences

- A PR that touches one folder runs that folder's job (and `changes`)
  only — the rest report as skipped.
- Skipped jobs count as successful for branch-protection required
  checks, so listing the per-implementation jobs as required still works:
  an unrelated PR passes them by skipping.
- Trade-off of the per-folder rule: an `openapi/**` edit is not validated
  against the implementations on that PR. If a contract change needs to be
  proven across implementations in one PR, that PR has to also touch the
  implementation folders (which it should anyway, per AGENTS.md's
  "check whether other backends now need the same change"). A change to
  `ci.yml` is *not* subject to this — it re-runs everything.
- A PR that edits `ci.yml` runs the full matrix, so trivial workflow
  tweaks are the expensive case. Acceptable: workflow changes are rare
  next to implementation changes.
- Adding an implementation folder now means adding both a job and a
  matching `changes` filter (with its `*pipeline` anchor line) + output;
  the header comment in `ci.yml` says so.
