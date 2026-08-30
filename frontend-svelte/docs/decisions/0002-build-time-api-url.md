# 2. Backend API URL is a build-time value

## Status

Accepted

## Context

The frontend needs to know the backend's base URL (`VITE_API_BASE_URL`).
Vite inlines `import.meta.env.*` values at build time — there is no
runtime `.env` reload for a static SPA build the way there is for the Go
backend. Identical situation to `frontend-react`
(`frontend-react/docs/decisions/0002-build-time-api-url.md`), since both
use the same Vite build.

## Decision

Treat `VITE_API_BASE_URL` as a **build-time** argument (`docker build
--build-arg`), not a runtime environment variable. The Docker image bakes
in one backend URL; pointing the same built image at a different backend
requires rebuilding the image.

## Consequences

- Simple: no runtime config-injection script, no `window.ENV` shim, no
  extra nginx templating step.
- Trade-off: can't reuse one built frontend image across environments
  with different backend URLs without rebuilding. Acceptable for this
  project (a testbed comparing implementations, not a multi-environment
  production deployment).
