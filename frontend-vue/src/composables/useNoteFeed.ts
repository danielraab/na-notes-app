import { ref } from 'vue';
import { api } from '../api/client';
import type { NoteSummary } from '../api/types';

// Backs the dashboard's cursor-paginated "load more on scroll" feed
// (ADR 0007). The cursor itself is opaque — this composable only ever
// passes back the nextCursor it was given, never inspects or constructs
// one. Call it fresh (e.g. keyed by the signed-in user's id) rather than
// sharing one instance across users.
export function useNoteFeed() {
  const notes = ref<NoteSummary[]>([]);
  const hasMore = ref(true);
  const loading = ref(true);
  const error = ref<string | null>(null);
  let cursor: string | null = null;
  let loadingNow = false;

  async function loadMore(): Promise<void> {
    if (loadingNow || !hasMore.value) return;
    loadingNow = true;
    loading.value = true;
    error.value = null;
    try {
      const page = await api.listNotes(cursor);
      cursor = page.nextCursor;
      notes.value = [...notes.value, ...page.items];
      hasMore.value = page.nextCursor !== null;
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load notes';
    } finally {
      loadingNow = false;
      loading.value = false;
    }
  }

  loadMore();

  return { notes, loading, hasMore, error, loadMore };
}
