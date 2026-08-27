import { Link } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

export function Header() {
  const { user, loading, login, logout } = useAuth();

  return (
    <header className="flex items-center justify-between border-b border-line px-6 py-3">
      <Link to="/" className="text-lg font-bold text-fg no-underline">
        NA Notes
      </Link>
      <div className="flex items-center gap-3">
        {!loading && user && (
          <>
            <Link to="/notes/new" className="text-sm text-accent">
              New note
            </Link>
            <span className="text-sm text-fg-muted">{user.displayName}</span>
            <button type="button" className="btn" onClick={() => logout()}>
              Log out
            </button>
          </>
        )}
        {!loading && !user && (
          <button type="button" className="btn" onClick={() => login('/')}>
            Log in
          </button>
        )}
      </div>
    </header>
  );
}
