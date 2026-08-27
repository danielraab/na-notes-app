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
          <div className="mb-5 flex items-center justify-between gap-4 rounded-lg border border-line bg-canvas-subtle px-4 py-3">
            <p>Sign in to create your own notes, share them, and mention teammates.</p>
            <button type="button" className="btn" onClick={() => login('/')}>
              Log in
            </button>
          </div>
          <div className="flex justify-center py-6">
            <NotePreview />
          </div>
        </>
      )}
    </div>
  );
}
