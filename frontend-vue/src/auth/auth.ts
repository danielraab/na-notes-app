import { reactive, readonly } from 'vue';
import { ApiError, api, loginUrl } from '../api/client';
import type { User } from '../api/types';

// Shared auth state as a module-level reactive singleton rather than a
// provide/inject context: `auth.user` / `auth.loading` are reactive
// wherever this module is imported, no wrapper component needed. Mirrors
// the singleton-store pattern the Svelte implementation uses
// (src/auth/auth.svelte.ts), adapted to Vue's `reactive()`.
interface AuthState {
  user: User | null;
  loading: boolean;
}

const state = reactive<AuthState>({
  user: null,
  loading: true,
});

api
  .me()
  .then((u) => {
    state.user = u;
  })
  .catch((err: unknown) => {
    if (!(err instanceof ApiError && err.status === 401)) {
      console.error('failed to load current user', err);
    }
  })
  .finally(() => {
    state.loading = false;
  });

function login(redirectTo: string = window.location.pathname): void {
  window.location.href = loginUrl(redirectTo);
}

async function logout(): Promise<void> {
  await api.logout();
  state.user = null;
}

export function useAuth() {
  return {
    state: readonly(state),
    login,
    logout,
  };
}
