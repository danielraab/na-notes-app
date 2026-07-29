import { Link } from 'react-router-dom';
import type { NoteSummary } from '../api/types';

const PERMISSION_LABEL: Record<NoteSummary['myPermission'], string> = {
  owner: 'Owner',
  edit: 'Can edit',
  read: 'Read only',
};

export function NoteCard({ note }: { note: NoteSummary }) {
  return (
    <Link to={`/notes/${note.id}`} className="note-card">
      <h3>{note.title}</h3>
      <p className="note-card__excerpt">{note.excerpt}</p>
      <div className="note-card__meta">
        <span>{PERMISSION_LABEL[note.myPermission]}</span>
        {note.isPublic && <span>Public</span>}
        <time dateTime={note.updatedAt}>{new Date(note.updatedAt).toLocaleDateString()}</time>
      </div>
    </Link>
  );
}
