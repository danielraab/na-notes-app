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
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h2>Share note</h2>
        {error && <p className="error-banner">{error}</p>}

        <section>
          <h3>Share with a person</h3>
          <div className="share-form">
            <input
              type="text"
              placeholder="Search by name or email"
              value={selectedUser ? selectedUser.displayName : query}
              onChange={(e) => {
                setSelectedUser(null);
                setQuery(e.target.value);
              }}
            />
            {results.length > 0 && !selectedUser && (
              <ul className="mention-suggestions">
                {results.map((user) => (
                  <li key={user.id}>
                    <button type="button" onClick={() => setSelectedUser(user)}>
                      {user.displayName}
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <select value={permission} onChange={(e) => setPermission(e.target.value as SharePermission)}>
              <option value="read">Read only</option>
              <option value="edit">Can edit</option>
            </select>
            <button type="button" onClick={handleShare} disabled={!selectedUser}>
              Share
            </button>
          </div>

          <ul className="share-list">
            {shares?.userShares.map((share) => (
              <li key={share.user.id}>
                <span>{share.user.displayName}</span>
                <span>{share.permission === 'edit' ? 'Can edit' : 'Read only'}</span>
                <button type="button" onClick={() => handleRevoke(share.user.id)}>
                  Remove
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section>
          <h3>Public link</h3>
          {shares?.publicShare ? (
            <div className="public-link">
              <input type="text" readOnly value={shares.publicShare.url} onFocus={(e) => e.target.select()} />
              <button type="button" onClick={handleRevokePublicLink}>
                Revoke
              </button>
            </div>
          ) : (
            <button type="button" onClick={handleCreatePublicLink}>
              Create public link (read-only)
            </button>
          )}
        </section>

        <button type="button" onClick={onClose} className="dialog__close">
          Done
        </button>
      </div>
    </div>
  );
}
