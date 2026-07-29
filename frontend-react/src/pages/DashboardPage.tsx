import { useAuth } from '../auth/useAuth';
import { NoteGrid } from '../components/NoteGrid';

export function DashboardPage() {
  const { user, loading, login } = useAuth();

  if (loading) return <p className="page">Loading…</p>;

  return (
    <div className="page">
      {!user && (
        <div className="hint-banner">
          <p>Sign in to create your own notes, share them, and mention teammates.</p>
          <button type="button" onClick={() => login('/')}>
            Log in
          </button>
        </div>
      )}
      <NoteGrid key={user?.id ?? 'anon'} />
    </div>
  );
}
