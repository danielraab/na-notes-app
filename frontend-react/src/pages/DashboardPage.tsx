import { useAuth } from '../auth/useAuth';
import { NoteGrid } from '../components/NoteGrid';
import { NotePreview } from '../components/NotePreview';

export function DashboardPage() {
  const { user, loading, login } = useAuth();

  if (loading) return <p className="page">Loading…</p>;

  return (
    <div className="page">
      {user ? (
        <NoteGrid key={user.id} />
      ) : (
        <>
          <div className="hint-banner">
            <p>Sign in to create your own notes, share them, and mention teammates.</p>
            <button type="button" onClick={() => login('/')}>
              Log in
            </button>
          </div>
          <div className="note-preview-wrap">
            <NotePreview />
          </div>
        </>
      )}
    </div>
  );
}
