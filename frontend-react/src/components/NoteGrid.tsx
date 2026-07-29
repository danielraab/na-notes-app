import { useEffect, useRef } from 'react';
import { useNoteFeed } from '../hooks/useNoteFeed';
import { NoteCard } from './NoteCard';

export function NoteGrid() {
  const { notes, loading, hasMore, error, loadMore } = useNoteFeed();
  const sentinelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !hasMore) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) loadMore();
      },
      { rootMargin: '200px' },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, loadMore]);

  if (error) {
    return <p className="error-banner">{error}</p>;
  }

  return (
    <>
      <div className="note-grid">
        {notes.map((note) => (
          <NoteCard key={note.id} note={note} />
        ))}
      </div>
      {loading && <p className="note-grid__status">Loading…</p>}
      {!loading && notes.length === 0 && <p className="note-grid__status">No notes yet.</p>}
      <div ref={sentinelRef} />
    </>
  );
}
