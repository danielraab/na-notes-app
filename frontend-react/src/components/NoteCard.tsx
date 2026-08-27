import { Link } from 'react-router-dom';
import type { NoteSummary } from '../api/types';

const PERMISSION_LABEL: Record<NoteSummary['myPermission'], string> = {
  owner: 'Owner',
  edit: 'Can edit',
  read: 'Read only',
};

export function NoteCard({ note }: { note: NoteSummary }) {
  return (
    <Link
      to={`/notes/${note.id}`}
      className="block rounded-lg border border-line bg-canvas-subtle p-4 text-fg no-underline transition-colors hover:border-accent"
    >
      <h3 className="mb-2 text-base font-semibold">{note.title}</h3>
      <p className="mb-3 line-clamp-3 text-sm text-fg-muted">{note.excerpt}</p>
      <div className="flex gap-2.5 text-xs text-fg-muted">
        <span>{PERMISSION_LABEL[note.myPermission]}</span>
        {note.isPublic && <span>Public</span>}
        <time dateTime={note.updatedAt}>{new Date(note.updatedAt).toLocaleDateString()}</time>
      </div>
    </Link>
  );
}
