import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { NoteSummary } from '../api/types';

// Backs the dashboard's cursor-paginated "load more on scroll" feed
// (ADR 0007). The cursor itself is opaque — this hook only ever passes
// back the nextCursor it was given, never inspects or constructs one.
export function useNoteFeed() {
  const [notes, setNotes] = useState<NoteSummary[]>([]);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const cursorRef = useRef<string | null>(null);
  const loadingRef = useRef(false);
  const didInitRef = useRef(false);

  const loadMore = useCallback(async () => {
    if (loadingRef.current || !hasMore) return;
    loadingRef.current = true;
    setLoading(true);
    setError(null);
    try {
      const page = await api.listNotes(cursorRef.current);
      cursorRef.current = page.nextCursor;
      setNotes((prev) => [...prev, ...page.items]);
      setHasMore(page.nextCursor !== null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load notes');
    } finally {
      loadingRef.current = false;
      setLoading(false);
    }
  }, [hasMore]);

  useEffect(() => {
    // Guards against React StrictMode's dev-only double-invocation of
    // mount effects, which would otherwise fire two overlapping initial
    // requests and append the first page twice. Callers remount this hook
    // (via a `key`) when the signed-in user changes instead of
    // re-triggering here.
    if (didInitRef.current) return;
    didInitRef.current = true;
    loadMore();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { notes, loading, hasMore, error, loadMore };
}
