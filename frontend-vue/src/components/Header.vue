<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { API_BASE_URL } from '../api/client';
import { useAuth } from '../auth/auth';

const { state: auth, login, logout } = useAuth();

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

const menuOpen = ref(false);
const containerRef = ref<HTMLDivElement | null>(null);

function handlePointerDown(event: MouseEvent) {
  if (!containerRef.value?.contains(event.target as Node)) {
    menuOpen.value = false;
  }
}

function handleKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') menuOpen.value = false;
}

onMounted(() => {
  document.addEventListener('mousedown', handlePointerDown);
  document.addEventListener('keydown', handleKeyDown);
});

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handlePointerDown);
  document.removeEventListener('keydown', handleKeyDown);
});

function handleLogout() {
  menuOpen.value = false;
  logout();
}
</script>

<template>
  <header class="site-header">
    <div class="site-header-inner">
      <div class="brand-group">
        <RouterLink to="/" class="brand">
          <span class="brand-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
            </svg>
          </span>
          <span class="brand-name">NA Notes</span>
        </RouterLink>
        <span class="brand-meta">Vue &middot; {{ API_BASE_URL }}</span>
      </div>

      <nav class="header-nav">
        <template v-if="!auth.loading && auth.user">
          <RouterLink to="/notes/new" class="btn btn-primary">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M12 5v14M5 12h14" />
            </svg>
            <span>New note</span>
          </RouterLink>

          <div class="user-menu" ref="containerRef">
            <button
              type="button"
              class="user-menu-trigger"
              aria-haspopup="menu"
              :aria-expanded="menuOpen"
              aria-label="Account menu"
              @click="menuOpen = !menuOpen"
            >
              {{ initials(auth.user.displayName) }}
            </button>

            <div v-if="menuOpen" role="menu" class="user-menu-panel">
              <div class="user-menu-header">
                <p class="user-menu-name">{{ auth.user.displayName }}</p>
                <p class="user-menu-email">{{ auth.user.email }}</p>
              </div>
              <div>
                <button type="button" role="menuitem" class="user-menu-item" @click="handleLogout">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                    <path d="m16 17 5-5-5-5" />
                    <path d="M21 12H9" />
                  </svg>
                  Log out
                </button>
              </div>
            </div>
          </div>
        </template>
        <button v-else-if="!auth.loading" type="button" class="btn btn-primary" @click="login('/')">
          Log in
        </button>
      </nav>
    </div>
  </header>
</template>
