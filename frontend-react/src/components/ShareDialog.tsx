import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { SharePermission, SharesResponse, UserSummary } from '../api/types';

interface Props {
  noteId: string;
  onClose: () => void;
}

export function ShareDialog({ noteId, onClose }: Props) {
  const [shares, setShares] = useState<SharesResponse | null>(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserSummary[]>([]);
  const [selectedUser, setSelectedUser] = useState<UserSummary | null>(null);
  const [permission, setPermission] = useState<SharePermission>('read');
  const [error, setError] = useState<string | null>(null);

  function reload() {
    api.listShares(noteId).then(setShares).catch((err: unknown) => {
      setError(err instanceof Error ? err.message : 'Failed to load shares');
    });
  }

  useEffect(reload, [noteId]);

  useEffect(() => {
    if (query.length === 0) {
      setResults([]);
      return;
    }
    const timeoutId = setTimeout(() => {
      api.searchUsers(query).then(setResults).catch(() => setResults([]));
    }, 200);
    return () => clearTimeout(timeoutId);
  }, [query]);

  async function handleShare() {
    if (!selectedUser) return;
    setError(null);
    try {
      await api.shareWithUser(noteId, selectedUser.id, permission);
      setSelectedUser(null);
      setQuery('');
      reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to share note');
    }
  }

  async function handleRevoke(userId: string) {
    await api.revokeShare(noteId, userId);
    reload();
  }

  async function handleCreatePublicLink() {
    await api.createPublicShare(noteId);
    reload();
  }

  async function handleRevokePublicLink() {
    await api.revokePublicShare(noteId);
    reload();
  }

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="max-h-[90vh] w-full max-w-[480px] overflow-y-auto rounded-xl border border-line bg-canvas p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="mb-4 text-xl font-semibold">Share note</h2>
        {error && <p className="mb-3 text-sm text-danger">{error}</p>}

        <section className="mb-5">
          <h3 className="mb-2 text-sm font-semibold text-fg-muted">Share with a person</h3>
          <div className="relative mb-3 flex flex-wrap gap-2">
            <input
              type="text"
              className="field min-w-[160px] flex-1"
              placeholder="Search by name or email"
              value={selectedUser ? selectedUser.displayName : query}
              onChange={(e) => {
                setSelectedUser(null);
                setQuery(e.target.value);
              }}
            />
            {results.length > 0 && !selectedUser && (
              <ul className="absolute left-0 top-full z-10 mt-1 max-w-[260px] list-none rounded-md border border-line bg-canvas p-1">
                {results.map((user) => (
                  <li key={user.id}>
                    <button
                      type="button"
                      className="block w-full rounded px-2 py-1 text-left text-sm hover:bg-canvas-subtle"
                      onClick={() => setSelectedUser(user)}
                    >
                      {user.displayName}
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <select
              className="field"
              value={permission}
              onChange={(e) => setPermission(e.target.value as SharePermission)}
            >
              <option value="read">Read only</option>
              <option value="edit">Can edit</option>
            </select>
            <button type="button" className="btn" onClick={handleShare} disabled={!selectedUser}>
              Share
            </button>
          </div>

          <ul className="space-y-1">
            {shares?.userShares.map((share) => (
              <li key={share.user.id} className="flex items-center gap-2.5 py-1.5">
                <span className="flex-1">{share.user.displayName}</span>
                <span className="text-sm text-fg-muted">
                  {share.permission === 'edit' ? 'Can edit' : 'Read only'}
                </span>
                <button type="button" className="btn" onClick={() => handleRevoke(share.user.id)}>
                  Remove
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section className="mb-5">
          <h3 className="mb-2 text-sm font-semibold text-fg-muted">Public link</h3>
          {shares?.publicShare ? (
            <div className="flex gap-2">
              <input
                type="text"
                className="field flex-1"
                readOnly
                value={shares.publicShare.url}
                onFocus={(e) => e.target.select()}
              />
              <button type="button" className="btn" onClick={handleRevokePublicLink}>
                Revoke
              </button>
            </div>
          ) : (
            <button type="button" className="btn" onClick={handleCreatePublicLink}>
              Create public link (read-only)
            </button>
          )}
        </section>

        <button type="button" className="btn" onClick={onClose}>
          Done
        </button>
      </div>
    </div>
  );
}
