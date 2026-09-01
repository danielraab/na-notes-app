# 16. CI publishes implementation images to GHCR

## Status

Accepted.

## Context

Every implementation job in `.github/workflows/ci.yml` already builds a
Docker image (`na-notes-backend-go`, `na-notes-frontend-vue`, …) purely
to prove the `Dockerfile` still builds — the image was thrown away at the
end of the job. `backend-quarkus-native` additionally stamped its image
with a per-day incremental version `YYYYMMDD-N`, kept in git tags, but
also kept the result only locally.

We want the built images to be pullable — for deployments and for anyone
who would rather `docker pull` than build nine implementations locally.
GitHub Container Registry (`ghcr.io`) is the natural target: no extra
account or secret, `GITHUB_TOKEN` can push to it with
`permissions: packages: write`.

## Decision

- **Registry / naming:** `ghcr.io/<repository_owner>/na-notes-<impl>`,
  reusing the image names the jobs already use — `na-notes-backend-go`,
  `na-notes-backend-quarkus`, `na-notes-backend-quarkus-native`,
  `na-notes-frontend-react`, etc.
- **When:** only on `push` to `main`. Pull requests still build every
  relevant image (Dockerfile regression check) but never log in or push —
  this also keeps fork PRs working, since they have no package-write
  token.
- **Combined with ADR 0015:** a push only builds+pushes the folders that
  changed. A one-file change to `frontend-vue` publishes a new
  `na-notes-frontend-vue` image and nothing else.
- **Tag:** a single per-run version `YYYYMMDD-N`, generalising the scheme
  `backend-quarkus-native` used. The `changes` gate job (ADR 0015)
  computes it once per push and records it as a git tag `build-YYYYMMDD-N`;
  that tag is also the per-day counter for `N`, and the version is exposed
  as a `changes` job output. Every image pushed by that run gets the same
  tag, so the images from one push are a matched set
  (`…/na-notes-backend-go:20260901-3` and
  `…/na-notes-frontend-vue:20260901-3` came from the same commit).
  No `latest` tag — deployments pin an exact `YYYYMMDD-N`.
- `backend-quarkus-native` no longer maintains its own
  `backend-quarkus-native-YYYYMMDD-N` git tag; it uses the shared
  `version` job like every other image.
- `permissions:` at the workflow level is `contents: read` +
  `packages: write`; the `changes` job overrides with `contents: write`
  so it can push the `build-*` git tag.

## Consequences

- Consuming the images needs the exact version tag — check the `version`
  job's log (or `git tag -l 'build-*'`) for the latest. `docker-compose.yml`
  still builds from source (ADR 0010) and is unchanged; pointing a service
  at a published image is a manual `image:` edit.
- A CI run that fails after the `changes` job has pushed its tag burns
  that number — `N` has gaps, which is harmless for a monotonic counter.
- Two pushes to `main` in the same minute race on `git push origin
  build-YYYYMMDD-N`; the loser's tag push fails the run. Same race the old
  `backend-quarkus-native` tagging had; acceptable at this repo's rate.
- New GHCR packages are created private and linked to the repo. Making
  them public (or granting pull access) is a one-time manual step in the
  package settings.
- Adding an implementation folder now also means adding the
  login + tag + push step to its job (three lines, copied from a sibling),
  in addition to the job and `changes` filter from ADR 0015.
