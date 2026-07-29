import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { PublicNoteView } from '../api/types';
import { MarkdownView } from '../components/MarkdownView';

export function PublicNotePage() {
  const { token } = useParams<{ token: string }>();
  const [note, setNote] = useState<PublicNoteView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    api
      .getPublicNote(token)
      .then(setNote)
      .catch(() => setError('This note is not available. The link may have been revoked.'));
  }, [token]);

  if (error) {
    return (
      <div className="page">
        <p className="error-banner">{error}</p>
      </div>
    );
  }
  if (!note) {
    return (
      <div className="page">
        <p>Loading…</p>
      </div>
    );
  }

  return (
    <div className="page">
      <p className="public-note-banner">Shared read-only note</p>
      <h1>{note.title}</h1>
      <MarkdownView markdown={note.contentMarkdown} />
    </div>
  );
}
