<script lang="ts">
  import { currentPath, matchRoute } from './lib/router';
  import Header from './components/Header.svelte';
  import DashboardPage from './pages/DashboardPage.svelte';
  import NoteEditorPage from './pages/NoteEditorPage.svelte';
  import PublicNotePage from './pages/PublicNotePage.svelte';

  let noteMatch = $derived(matchRoute('/notes/:id', $currentPath));
  let shareMatch = $derived(matchRoute('/shared/:token', $currentPath));
</script>

<Header />
<main>
  {#if $currentPath === '/'}
    <DashboardPage />
  {:else if $currentPath === '/notes/new'}
    {#key 'new'}
      <NoteEditorPage />
    {/key}
  {:else if noteMatch}
    {#key noteMatch.params.id}
      <NoteEditorPage id={noteMatch.params.id} />
    {/key}
  {:else if shareMatch}
    {#key shareMatch.params.token}
      <PublicNotePage token={shareMatch.params.token} />
    {/key}
  {:else}
    <p class="page">Page not found.</p>
  {/if}
</main>
