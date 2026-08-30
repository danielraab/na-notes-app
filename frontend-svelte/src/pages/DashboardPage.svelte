<script lang="ts">
  import { auth } from '../auth/auth.svelte';
  import NoteGrid from '../components/NoteGrid.svelte';
  import NotePreview from '../components/NotePreview.svelte';
</script>

{#if auth.loading}
  <p class="page">Loading…</p>
{:else}
  <div class="page">
    {#if auth.user}
      {#key auth.user.id}
        <NoteGrid />
      {/key}
    {:else}
      <div class="banner">
        <p>Sign in to create your own notes, share them, and mention teammates.</p>
        <button type="button" class="btn" onclick={() => auth.login('/')}>Log in</button>
      </div>
      <div class="preview-row">
        <NotePreview />
      </div>
    {/if}
  </div>
{/if}
