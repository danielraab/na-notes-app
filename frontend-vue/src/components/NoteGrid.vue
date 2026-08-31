<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { useNoteFeed } from '../composables/useNoteFeed';
import NoteCard from './NoteCard.vue';

const { notes, loading, hasMore, error, loadMore } = useNoteFeed();

const sentinelRef = ref<HTMLDivElement | null>(null);
let observer: IntersectionObserver | null = null;

watch(
  [sentinelRef, hasMore],
  ([sentinel, more]) => {
    observer?.disconnect();
    if (!sentinel || !more) return;
    observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) loadMore();
      },
      { rootMargin: '200px' },
    );
    observer.observe(sentinel);
  },
  { immediate: true },
);

onBeforeUnmount(() => observer?.disconnect());
</script>

<template>
  <p v-if="error" class="text-danger">{{ error }}</p>
  <template v-else>
    <div class="note-grid">
      <NoteCard v-for="note in notes" :key="note.id" :note="note" />
    </div>
    <p v-if="loading" class="feed-status">Loading…</p>
    <p v-if="!loading && notes.length === 0" class="feed-status">No notes yet.</p>
    <div ref="sentinelRef" />
  </template>
</template>
