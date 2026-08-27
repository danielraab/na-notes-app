import { useEffect, useRef, useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

interface UserMenuProps {
  displayName: string;
  email: string;
  onLogout: () => void;
}

function UserMenu({ displayName, email, onLogout }: UserMenuProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false);
    }

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        className="flex h-8 w-8 items-center justify-center rounded-full bg-canvas-subtle text-xs font-semibold text-fg-muted ring-1 ring-line transition-colors hover:text-fg hover:ring-accent"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account menu"
        onClick={() => setOpen((v) => !v)}
      >
        {initials(displayName)}
      </button>

      {open && (
        <div
          role="menu"
          className="absolute right-0 top-full z-50 mt-2 w-56 overflow-hidden rounded-xl border border-line bg-canvas shadow-lg shadow-black/5"
        >
          <div className="border-b border-line px-3 py-2.5">
            <p className="truncate text-sm font-medium text-fg">{displayName}</p>
            <p className="truncate text-xs text-fg-muted">{email}</p>
          </div>
          <div className="p-1">
            <button
              type="button"
              role="menuitem"
              className="flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left text-sm text-fg transition-colors hover:bg-canvas-subtle"
              onClick={() => {
                setOpen(false);
                onLogout();
              }}
            >
              <svg
                width="15"
                height="15"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                <path d="m16 17 5-5-5-5" />
                <path d="M21 12H9" />
              </svg>
              Log out
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export function Header() {
  const { user, loading, login, logout } = useAuth();

  return (
    <header className="sticky top-0 z-50 border-b border-line bg-canvas/80 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-[960px] items-center justify-between px-4">
        <Link
          to="/"
          className="group flex items-center gap-2 text-fg no-underline"
        >
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-accent text-accent-fg transition-transform group-hover:scale-105">
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
            </svg>
          </span>
          <span className="text-base font-semibold tracking-tight">NA Notes</span>
        </Link>

        <nav className="flex items-center gap-2 sm:gap-3">
          {!loading && user && (
            <>
              <NavLink
                to="/notes/new"
                className={({ isActive }) =>
                  `btn btn-primary ${isActive ? 'brightness-110' : ''}`
                }
              >
                <svg
                  width="15"
                  height="15"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <path d="M12 5v14M5 12h14" />
                </svg>
                <span className="hidden sm:inline">New note</span>
              </NavLink>

              <UserMenu
                displayName={user.displayName}
                email={user.email}
                onLogout={() => logout()}
              />
            </>
          )}
          {!loading && !user && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => login('/')}
            >
              Log in
            </button>
          )}
        </nav>
      </div>
    </header>
  );
}
