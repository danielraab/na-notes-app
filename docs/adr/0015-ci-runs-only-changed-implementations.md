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
- Filtering is **strictly per folder**. A change to `openapi/**` does
  *not* re-run the backend/frontend jobs, and neither does a change to
  `.github/workflows/ci.yml` itself. The contract stays enforced by each
  implementation's own contract tests when that implementation next
  changes, and by review.

## Consequences

- A PR that touches one folder runs that folder's job (and `changes`)
  only — the rest report as skipped.
- Skipped jobs count as successful for branch-protection required
  checks, so listing the per-implementation jobs as required still works:
  an unrelated PR passes them by skipping.
- Trade-off of the strict per-folder rule: an `openapi/**` edit or a
  change to a job's own build steps in `ci.yml` is not validated against
  the implementations on that PR. If a contract change needs to be
  proven across implementations in one PR, that PR has to also touch the
  implementation folders (which it should anyway, per AGENTS.md's
  "check whether other backends now need the same change").
- Adding an implementation folder now means adding both a job and a
  matching `changes` filter + output; the header comment in `ci.yml`
  says so.
