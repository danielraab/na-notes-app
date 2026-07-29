# 5. Kept react-router-dom latest despite one npm audit advisory

## Status

Accepted

## Context

`npm audit` flags the installed `react-router` range with a "high"
severity advisory: "RSC Mode CSRF Bypass Allows Action Execution Before
400 Response" (GHSA-qwww-vcr4-c8h2). The suggested fix
(`npm audit fix --force`) downgrades to `react-router-dom@7.11.0`, which
in turn is flagged for roughly ten *other* advisories (XSS, DoS, open
redirect, CSRF) that were fixed in later releases.

## Decision

Keep `react-router-dom` on latest (`^7.18.2` at time of writing). The
GHSA-qwww-vcr4-c8h2 advisory is specific to React Router's **RSC
(framework) mode with server actions** — this app is a plain client-side
SPA (`<BrowserRouter>`, no server actions, no RSC), so the vulnerable code
path is not reachable here. Downgrading to "fix" it would trade one
inapplicable advisory for several applicable ones.

## Consequences

- `npm audit` will continue to show this one advisory as unresolved;
  that's expected and intentional, not an oversight.
- If this app ever adopts React Router's framework/RSC mode, revisit
  this decision and upgrade past the fixed version first.
