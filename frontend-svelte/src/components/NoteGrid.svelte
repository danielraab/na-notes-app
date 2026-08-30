<script lang="ts">
  import { createNoteFeed } from '../hooks/noteFeed.svelte';
  import NoteCard from './NoteCard.svelte';

  const feed = createNoteFeed();

  let sentinelEl: HTMLDivElement | undefined = $state();

  $effect(() => {
    const sentinel = sentinelEl;
    if (!sentinel || !feed.hasMore) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) feed.loadMore();
      },
      { rootMargin: '200px' },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  });
</script>

{#if feed.error}
  <p class="text-danger">{feed.error}</p>
{:else}
  <div class="note-grid">
    {#each feed.notes as note (note.id)}
      <NoteCard {note} />
    {/each}
  </div>
  {#if feed.loading}
    <p class="feed-status">Loading…</p>
  {/if}
  {#if !feed.loading && feed.notes.length === 0}
    <p class="feed-status">No notes yet.</p>
  {/if}
  <div bind:this={sentinelEl}></div>
{/if}
