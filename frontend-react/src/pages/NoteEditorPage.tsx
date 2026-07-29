import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { VersionConflictError, api } from '../api/client';
import type { Note } from '../api/types';
import { MarkdownEditor } from '../components/MarkdownEditor';
import { MarkdownView } from '../components/MarkdownView';
import { ShareDialog } from '../components/ShareDialog';
import { extractMentionedUserIds } from '../utils/mentions';

export function NoteEditorPage() {
  const { id } = useParams<{ id: string }>();
  const isNew = id === undefined;
  const navigate = useNavigate();
  const { user, loading: authLoading, login } = useAuth();

  const [note, setNote] = useState<Note | null>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<Note | null>(null);
  const [showShare, setShowShare] = useState(false);

  useEffect(() => {
    if (isNew || !id) return;
    setLoading(true);
    api
      .getNote(id)
      .then((n) => {
        setNote(n);
        setTitle(n.title);
        setContent(n.contentMarkdown);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load note'))
      .finally(() => setLoading(false));
  }, [id, isNew]);

  if (!authLoading && !user) {
    return (
      <div className="page">
        <p>You need to be signed in to {isNew ? 'create' : 'view'} this note.</p>
        <button type="button" onClick={() => login(window.location.pathname)}>
          Log in
        </button>
      </div>
    );
  }

  if (loading) return <p className="page">Loading…</p>;
  if (error) return <p className="page error-banner">{error}</p>;

  const readOnly = note !== null && note.myPermission === 'read';
  const canManageSharing = note?.myPermission === 'owner';
  const canDelete = note?.myPermission === 'owner';

  async function handleSave() {
    setSaving(true);
    setError(null);
    setConflict(null);
    const mentionedUserIds = extractMentionedUserIds(content);
    try {
      if (isNew) {
        const created = await api.createNote({ title, contentMarkdown: content, mentionedUserIds });
        navigate(`/notes/${created.id}`, { replace: true });
      } else if (note) {
        const updated = await api.updateNote(note.id, note.version, {
          title,
          contentMarkdown: content,
          mentionedUserIds,
        });
        setNote(updated);
      }
    } catch (err) {
      if (err instanceof VersionConflictError) {
        setConflict(err.currentNote);
      } else {
        setError(err instanceof Error ? err.message : 'Failed to save note');
      }
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!note || !window.confirm('Delete this note? This cannot be undone.')) return;
    await api.deleteNote(note.id);
    navigate('/');
  }

  function reloadLatest() {
    if (!conflict) return;
    setNote(conflict);
    setTitle(conflict.title);
    setContent(conflict.contentMarkdown);
    setConflict(null);
  }

  return (
    <div className="page note-editor-page">
      {conflict && (
        <div className="conflict-banner">
          <p>This note was changed by someone else since you opened it. Your unsaved edits are still below.</p>
          <button type="button" onClick={reloadLatest}>
            Load the latest version (discards your changes)
          </button>
        </div>
      )}

      <input
        className="note-editor-page__title"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Untitled note"
        disabled={readOnly}
      />

      {readOnly ? <MarkdownView markdown={content} /> : <MarkdownEditor value={content} onChange={setContent} />}

      <div className="note-editor-page__actions">
        {!readOnly && (
          <button type="button" onClick={handleSave} disabled={saving || title.trim() === ''}>
            {saving ? 'Saving…' : 'Save'}
          </button>
        )}
        {canManageSharing && (
          <button type="button" onClick={() => setShowShare(true)}>
            Share
          </button>
        )}
        {canDelete && (
          <button type="button" onClick={handleDelete} className="danger">
            Delete
          </button>
        )}
      </div>

      {showShare && note && <ShareDialog noteId={note.id} onClose={() => setShowShare(false)} />}
    </div>
  );
}
