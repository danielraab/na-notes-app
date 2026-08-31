import { Injectable, inject, signal } from '@angular/core';
import { Api, ApiError } from '../api/api';
import type { User } from '../api/types';

// App-lifetime singleton (providedIn: 'root'): the Angular equivalent of
// frontend-react's AuthContext provider and frontend-svelte's
// auth.svelte.ts singleton store — one place holding `user`/`loading` as
// signals, read directly by any component that injects `Auth`.
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly api = inject(Api);

  readonly user = signal<User | null>(null);
  readonly loading = signal(true);

  constructor() {
    this.api
      .me()
      .then((u) => this.user.set(u))
      .catch((err: unknown) => {
        if (!(err instanceof ApiError && err.status === 401)) {
          console.error('failed to load current user', err);
        }
      })
      .finally(() => this.loading.set(false));
  }

  login(redirectTo = window.location.pathname): void {
    window.location.href = this.api.loginUrl(redirectTo);
  }

  async logout(): Promise<void> {
    await this.api.logout();
    this.user.set(null);
  }
}
