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
  const [justSaved, setJustSaved] = useState(false);
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

  useEffect(() => {
    if (!justSaved) return;
    const timer = setTimeout(() => setJustSaved(false), 3000);
    return () => clearTimeout(timer);
  }, [justSaved]);

  if (!authLoading && !user) {
    return (
      <div className="page">
        <p className="mb-4">You need to be signed in to {isNew ? 'create' : 'view'} this note.</p>
        <button type="button" className="btn" onClick={() => login(window.location.pathname)}>
          Log in
        </button>
      </div>
    );
  }

  if (loading) return <p className="page">Loading…</p>;
  if (error) return <p className="page text-danger">{error}</p>;

  const readOnly = note !== null && note.myPermission === 'read';
  const canManageSharing = note?.myPermission === 'owner';
  const canDelete = note?.myPermission === 'owner';

  async function handleSave() {
    setSaving(true);
    setError(null);
    setConflict(null);
    setJustSaved(false);
    const mentionedUserIds = extractMentionedUserIds(content);
    try {
      if (isNew) {
        const created = await api.createNote({ title, contentMarkdown: content, mentionedUserIds });
        setJustSaved(true);
        navigate(`/notes/${created.id}`, { replace: true });
      } else if (note) {
        const updated = await api.updateNote(note.id, note.version, {
          title,
          contentMarkdown: content,
          mentionedUserIds,
        });
        setNote(updated);
        setJustSaved(true);
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
    <div className="page">
      {conflict && (
        <div className="mb-5 flex items-center justify-between gap-4 rounded-lg border border-line bg-canvas-subtle px-4 py-3">
          <p>This note was changed by someone else since you opened it. Your unsaved edits are still below.</p>
          <button type="button" className="btn" onClick={reloadLatest}>
            Load the latest version (discards your changes)
          </button>
        </div>
      )}

      <input
        className="mb-4 block w-full rounded-md border border-line bg-canvas px-2.5 py-2 text-2xl font-semibold text-fg outline-none focus:border-accent focus:ring-2 focus:ring-accent/30"
        value={title}
        onChange={(e) => {
          setTitle(e.target.value);
          setJustSaved(false);
        }}
        placeholder="Untitled note"
        disabled={readOnly}
      />

      {readOnly ? (
        <MarkdownView markdown={content} />
      ) : (
        <MarkdownEditor
          value={content}
          onChange={(value) => {
            setContent(value);
            setJustSaved(false);
          }}
        />
      )}

      <div className="mt-4 flex items-center gap-2.5">
        {!readOnly && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleSave}
            disabled={saving || title.trim() === ''}
          >
            {saving ? 'Saving…' : 'Save'}
          </button>
        )}
        {canManageSharing && (
          <button type="button" className="btn" onClick={() => setShowShare(true)}>
            Share
          </button>
        )}
        {canDelete && (
          <button type="button" className="btn btn-danger" onClick={handleDelete}>
            Delete
          </button>
        )}
        {justSaved && (
          <span role="status" className="inline-flex items-center gap-1.5 text-sm text-fg-muted">
            <span aria-hidden="true">✓</span> Saved
          </span>
        )}
      </div>

      {showShare && note && <ShareDialog noteId={note.id} onClose={() => setShowShare(false)} />}
    </div>
  );
}
