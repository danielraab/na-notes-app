export function NotePreview() {
  return (
    <div
      className="w-full max-w-80 cursor-default rounded-lg border border-dashed border-line bg-canvas-subtle p-4"
      aria-hidden="true"
    >
      <h3 className="mb-2 text-base font-semibold">Welcome to Notes</h3>
      <p className="mb-3 text-sm text-fg-muted">
        This is what a note looks like. Log in to write your own, format them with Markdown,
        share them with teammates, and mention people with @username.
      </p>
      <div className="flex gap-2.5 text-xs text-fg-muted">
        <span>Example</span>
        <span>Public</span>
        <time>Today</time>
      </div>
    </div>
  );
}
