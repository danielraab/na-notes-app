import { Component, effect, inject, input, output, signal } from '@angular/core';
import { Api } from '../../api/api';
import type { SharePermission, SharesResponse, UserSummary } from '../../api/types';

@Component({
  selector: 'app-share-dialog',
  templateUrl: './share-dialog.html',
  styleUrl: './share-dialog.css',
})
export class ShareDialog {
  private readonly api = inject(Api);

  readonly noteId = input.required<string>();
  readonly closed = output<void>();

  protected readonly shares = signal<SharesResponse | null>(null);
  protected readonly query = signal('');
  protected readonly results = signal<UserSummary[]>([]);
  protected readonly selectedUser = signal<UserSummary | null>(null);
  protected readonly permission = signal<SharePermission>('read');
  protected readonly error = signal<string | null>(null);

  private searchToken = 0;

  constructor() {
    effect(() => {
      this.noteId();
      this.reload();
    });
  }

  private reload(): void {
    this.api
      .listShares(this.noteId())
      .then((shares) => this.shares.set(shares))
      .catch((err: unknown) => {
        this.error.set(err instanceof Error ? err.message : 'Failed to load shares');
      });
  }

  protected onQueryInput(value: string): void {
    this.selectedUser.set(null);
    this.query.set(value);
    if (value.length === 0) {
      this.results.set([]);
      return;
    }
    const token = ++this.searchToken;
    setTimeout(() => {
      if (token !== this.searchToken) return;
      this.api
        .searchUsers(value)
        .then((results) => {
          if (token === this.searchToken) this.results.set(results);
        })
        .catch(() => {
          if (token === this.searchToken) this.results.set([]);
        });
    }, 200);
  }

  protected async handleShare(): Promise<void> {
    const user = this.selectedUser();
    if (!user) return;
    this.error.set(null);
    try {
      await this.api.shareWithUser(this.noteId(), user.id, this.permission());
      this.selectedUser.set(null);
      this.query.set('');
      this.reload();
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'Failed to share note');
    }
  }

  protected async handleRevoke(userId: string): Promise<void> {
    await this.api.revokeShare(this.noteId(), userId);
    this.reload();
  }

  protected async handleCreatePublicLink(): Promise<void> {
    await this.api.createPublicShare(this.noteId());
    this.reload();
  }

  protected async handleRevokePublicLink(): Promise<void> {
    await this.api.revokePublicShare(this.noteId());
    this.reload();
  }
}
