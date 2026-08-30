import { api } from '../api/client';
import type { NoteSummary } from '../api/types';

// Backs the dashboard's cursor-paginated "load more on scroll" feed
// (ADR 0007). The cursor itself is opaque — this only ever passes back
// the nextCursor it was given, never inspects or constructs one.
export function createNoteFeed() {
  let notes = $state<NoteSummary[]>([]);
  let hasMore = $state(true);
  let loading = $state(true);
  let error = $state<string | null>(null);
  let cursor: string | null = null;
  let loadingNow = false;

  async function loadMore(): Promise<void> {
    if (loadingNow || !hasMore) return;
    loadingNow = true;
    loading = true;
    error = null;
    try {
      const page = await api.listNotes(cursor);
      cursor = page.nextCursor;
      notes = [...notes, ...page.items];
      hasMore = page.nextCursor !== null;
    } catch (err) {
      error = err instanceof Error ? err.message : 'Failed to load notes';
    } finally {
      loadingNow = false;
      loading = false;
    }
  }

  loadMore();

  return {
    get notes() {
      return notes;
    },
    get hasMore() {
      return hasMore;
    },
    get loading() {
      return loading;
    },
    get error() {
      return error;
    },
    loadMore,
  };
}
