<script lang="ts">
  import { api } from '../api/client';
  import type { PublicNoteView } from '../api/types';
  import MarkdownView from '../components/MarkdownView.svelte';

  let { token }: { token: string } = $props();

  let note = $state<PublicNoteView | null>(null);
  let error = $state<string | null>(null);

  $effect(() => {
    api
      .getPublicNote(token)
      .then((n) => (note = n))
      .catch(() => {
        error = 'This note is not available. The link may have been revoked.';
      });
  });
</script>

{#if error}
  <div class="page">
    <p class="text-danger">{error}</p>
  </div>
{:else if !note}
  <div class="page">
    <p>Loading…</p>
  </div>
{:else}
  <div class="page">
    <p class="public-pill">Shared read-only note</p>
    <h1 class="public-note-title">{note.title}</h1>
    <MarkdownView markdown={note.contentMarkdown} />
  </div>
{/if}
