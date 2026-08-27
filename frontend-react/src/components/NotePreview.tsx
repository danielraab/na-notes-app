export function NotePreview() {
  return (
    <div className="note-card note-card--preview" aria-hidden="true">
      <h3>Welcome to Notes</h3>
      <p className="note-card__excerpt">
        This is what a note looks like. Log in to write your own, format them with Markdown,
        share them with teammates, and mention people with @username.
      </p>
      <div className="note-card__meta">
        <span>Example</span>
        <span>Public</span>
        <time>Today</time>
      </div>
    </div>
  );
}
