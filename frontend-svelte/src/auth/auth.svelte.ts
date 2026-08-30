import { ApiError, api, loginUrl } from '../api/client';
import type { User } from '../api/types';

// Shared auth state as a Svelte 5 runes class instead of a React-style
// context provider: `auth.user` / `auth.loading` are reactive anywhere
// this singleton is imported, no <AuthProvider> wrapper needed.
class AuthStore {
  user = $state<User | null>(null);
  loading = $state(true);

  constructor() {
    api
      .me()
      .then((u) => {
        this.user = u;
      })
      .catch((err: unknown) => {
        if (!(err instanceof ApiError && err.status === 401)) {
          console.error('failed to load current user', err);
        }
      })
      .finally(() => {
        this.loading = false;
      });
  }

  login(redirectTo: string = window.location.pathname): void {
    window.location.href = loginUrl(redirectTo);
  }

  async logout(): Promise<void> {
    await api.logout();
    this.user = null;
  }
}

export const auth = new AuthStore();
