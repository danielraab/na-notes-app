import { Link } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

export function Header() {
  const { user, loading, login, logout } = useAuth();

  return (
    <header className="app-header">
      <Link to="/" className="app-header__brand">
        NA Notes
      </Link>
      <div className="app-header__actions">
        {!loading && user && (
          <>
            <Link to="/notes/new">New note</Link>
            <span className="app-header__user">{user.displayName}</span>
            <button type="button" onClick={() => logout()}>
              Log out
            </button>
          </>
        )}
        {!loading && !user && (
          <button type="button" onClick={() => login('/')}>
            Log in
          </button>
        )}
      </div>
    </header>
  );
}
