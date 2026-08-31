<script lang="ts">
  import { API_BASE_URL } from '../api/client';
  import { auth } from '../auth/auth.svelte';
  import { link, navigate } from '../lib/router';

  let menuOpen = $state(false);
  let menuEl: HTMLDivElement | undefined = $state();

  function initials(name: string): string {
    const parts = name.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  function handlePointerDown(event: MouseEvent) {
    if (!menuEl?.contains(event.target as Node)) {
      menuOpen = false;
    }
  }

  function handleKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape') menuOpen = false;
  }

  $effect(() => {
    if (!menuOpen) return;
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  });

  async function handleLogout() {
    menuOpen = false;
    await auth.logout();
    navigate('/');
  }
</script>

<header class="site-header">
  <div class="site-header-inner">
    <div class="brand-group">
      <a href="/" class="brand" use:link>
        <span class="brand-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 20h9" />
            <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
          </svg>
        </span>
        <span class="brand-name">NA Notes</span>
      </a>
      <span class="brand-meta">Svelte &middot; {API_BASE_URL}</span>
    </div>

    <nav class="header-nav">
      {#if !auth.loading && auth.user}
        <a href="/notes/new" class="btn btn-primary" use:link>
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          <span>New note</span>
        </a>

        <div class="user-menu" bind:this={menuEl}>
          <button
            type="button"
            class="user-menu-trigger"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            aria-label="Account menu"
            onclick={() => (menuOpen = !menuOpen)}
          >
            {initials(auth.user.displayName)}
          </button>

          {#if menuOpen}
            <div role="menu" class="user-menu-panel">
              <div class="user-menu-header">
                <p class="user-menu-name">{auth.user.displayName}</p>
                <p class="user-menu-email">{auth.user.email}</p>
              </div>
              <button type="button" role="menuitem" class="user-menu-item" onclick={handleLogout}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                  <path d="m16 17 5-5-5-5" />
                  <path d="M21 12H9" />
                </svg>
                Log out
              </button>
            </div>
          {/if}
        </div>
      {/if}
      {#if !auth.loading && !auth.user}
        <button type="button" class="btn btn-primary" onclick={() => auth.login('/')}>
          Log in
        </button>
      {/if}
    </nav>
  </div>
</header>
