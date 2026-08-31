# 5. Signals for all component state; route params bound straight to component inputs

## Status

Accepted

## Context

`frontend-react` uses hooks (`useState`/`useEffect`/`useLayoutEffect`,
`useParams`) and `frontend-svelte` uses Svelte 5 runes
(`$state`/`$derived`/`$effect`). Angular's own current idiom for the same
job is signals — this decision records the Angular-specific primitives
used in place of each.

## Decision

- **`input()`/`model()`/`output()`** instead of `@Input()`/`@Output()`
  decorators, everywhere a component takes data or emits an event (e.g.
  `NoteCard.note`, `MarkdownEditor.value` as a `model()` two-way binding,
  `ShareDialog.closed`).
- **`linkedSignal`** for state that mirrors an input but needs local,
  optimistic overrides that reset when the input changes —
  `NoteCard.content` starts as `note().contentMarkdown` and is written to
  directly on a task-list toggle, with automatic rollback-on-input-change
  behavior for free if the card is ever reused for a different note.
- **`withComponentInputBinding()`** (passed to `provideRouter` in
  `app.config.ts`) binds route params directly to matching component
  `input()`s: `NoteEditorPage.id` and `PublicNotePage.token` are just
  signal inputs, populated from the `:id`/`:token` route segments with no
  `ActivatedRoute.paramMap` subscription written by hand.
  `NoteEditorPage` tells "new note" from "edit note" apart the same way
  `frontend-react`'s `useParams` check does — `id() === undefined` on the
  `notes/new` route, which has no `:id` segment.
- **`afterRenderEffect`** (not `effect`) for anything that needs to touch
  real DOM nodes after a render — `NoteCard`'s overflow-clamp measurement
  (`ResizeObserver` on the rendered card body, the equivalent of
  `frontend-react`'s `useLayoutEffect`) and `MarkdownView`'s
  checkbox-`disabled` toggling both use it, since a plain `effect` isn't
  guaranteed to run after the DOM it reads has been painted.
- **`effect()`** for everything else that needs to run a side effect when
  a signal changes: `NoteEditorPage` loading a note when its `id` input
  changes, `ShareDialog` reloading shares when `noteId` changes and
  debouncing its user search, the `justSaved` auto-clear timer (using the
  cleanup callback `effect((onCleanup) => ...)` gives you, the signals
  equivalent of a `useEffect` cleanup function).

## Consequences

- No `ActivatedRoute` boilerplate in either page component; route-driven
  state is just another signal input, reactive the same way any other
  input is.
- `afterRenderEffect` vs. `effect` is a real distinction to get right in
  this codebase — using a plain `effect` for DOM measurement would read
  stale layout on the same tick a signal changed. Follow the existing
  components' choice of which to use for a given kind of side effect
  rather than defaulting to `effect` everywhere.
- `linkedSignal` is newer and less commonly known than `signal`; a
  contributor unfamiliar with it should read `NoteCard.content`'s
  definition and its doc comment before reaching for a plain `signal` +
  manual reset `effect` in similar cases.
