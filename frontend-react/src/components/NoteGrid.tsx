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
    return <p className="text-danger">{error}</p>;
  }

  return (
    <>
      <div className="columns-[240px] gap-4 [&>*]:mb-4 [&>*]:break-inside-avoid">
        {notes.map((note) => (
          <NoteCard key={note.id} note={note} />
        ))}
      </div>
      {loading && <p className="py-6 text-center text-fg-muted">Loading…</p>}
      {!loading && notes.length === 0 && (
        <p className="py-6 text-center text-fg-muted">No notes yet.</p>
      )}
      <div ref={sentinelRef} />
    </>
  );
}
