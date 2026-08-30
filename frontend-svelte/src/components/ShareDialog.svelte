<script lang="ts">
  import { api } from '../api/client';
  import type { SharePermission, SharesResponse, UserSummary } from '../api/types';

  interface Props {
    noteId: string;
    onClose: () => void;
  }

  let { noteId, onClose }: Props = $props();

  let shares = $state<SharesResponse | null>(null);
  let query = $state('');
  let results = $state<UserSummary[]>([]);
  let selectedUser = $state<UserSummary | null>(null);
  let permission = $state<SharePermission>('read');
  let error = $state<string | null>(null);

  function reload() {
    api
      .listShares(noteId)
      .then((res) => (shares = res))
      .catch((err: unknown) => {
        error = err instanceof Error ? err.message : 'Failed to load shares';
      });
  }

  $effect(() => {
    void noteId;
    reload();
  });

  $effect(() => {
    if (query.length === 0) {
      results = [];
      return;
    }
    const q = query;
    const timeoutId = setTimeout(() => {
      api
        .searchUsers(q)
        .then((res) => (results = res))
        .catch(() => (results = []));
    }, 200);
    return () => clearTimeout(timeoutId);
  });

  async function handleShare() {
    if (!selectedUser) return;
    error = null;
    try {
      await api.shareWithUser(noteId, selectedUser.id, permission);
      selectedUser = null;
      query = '';
      reload();
    } catch (err) {
      error = err instanceof Error ? err.message : 'Failed to share note';
    }
  }

  async function handleRevoke(userId: string) {
    await api.revokeShare(noteId, userId);
    reload();
  }

  async function handleCreatePublicLink() {
    await api.createPublicShare(noteId);
    reload();
  }

  async function handleRevokePublicLink() {
    await api.revokePublicShare(noteId);
    reload();
  }
</script>

<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div class="dialog-overlay" onclick={onClose}>
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div class="dialog" onclick={(e) => e.stopPropagation()}>
    <h2>Share note</h2>
    {#if error}
      <p class="text-danger">{error}</p>
    {/if}

    <section>
      <h3>Share with a person</h3>
      <div class="share-search-row">
        <input
          type="text"
          class="field"
          placeholder="Search by name or email"
          value={selectedUser ? selectedUser.displayName : query}
          oninput={(e) => {
            selectedUser = null;
            query = (e.currentTarget as HTMLInputElement).value;
          }}
        />
        {#if results.length > 0 && !selectedUser}
          <ul class="suggest-list">
            {#each results as user (user.id)}
              <li>
                <button type="button" onclick={() => (selectedUser = user)}>
                  {user.displayName}
                </button>
              </li>
            {/each}
          </ul>
        {/if}
        <select class="field" bind:value={permission}>
          <option value="read">Read only</option>
          <option value="edit">Can edit</option>
        </select>
        <button type="button" class="btn" onclick={handleShare} disabled={!selectedUser}>
          Share
        </button>
      </div>

      <ul class="share-list">
        {#each shares?.userShares ?? [] as share (share.user.id)}
          <li class="share-row">
            <span class="share-row-name">{share.user.displayName}</span>
            <span class="share-row-permission">{share.permission === 'edit' ? 'Can edit' : 'Read only'}</span>
            <button type="button" class="btn" onclick={() => handleRevoke(share.user.id)}>Remove</button>
          </li>
        {/each}
      </ul>
    </section>

    <section>
      <h3>Public link</h3>
      {#if shares?.publicShare}
        <div class="public-link-row">
          <input
            type="text"
            class="field"
            readonly
            value={shares.publicShare.url}
            onfocus={(e) => (e.currentTarget as HTMLInputElement).select()}
          />
          <button type="button" class="btn" onclick={handleRevokePublicLink}>Revoke</button>
        </div>
      {:else}
        <button type="button" class="btn" onclick={handleCreatePublicLink}>
          Create public link (read-only)
        </button>
      {/if}
    </section>

    <button type="button" class="btn" onclick={onClose}>Done</button>
  </div>
</div>
