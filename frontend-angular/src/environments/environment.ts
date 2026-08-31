// Build-time value baked into the bundle — there is no runtime config
// reload for a static SPA build. Committed with a localhost default (this
// is a real TS module `Api` imports directly, unlike a `.env` file, so it
// has to exist for `tsc`/`ng build`/`ng test` to even compile — see
// docs/decisions/0002-build-time-api-url.md). Docker overwrites
// `apiBaseUrl` at image build time (see ../../Dockerfile); for `ng serve`
// against a non-default backend, just edit it directly.
export const environment = {
  apiBaseUrl: 'http://localhost:8080/api',
};
