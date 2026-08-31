import { Component, effect, inject, input, signal } from '@angular/core';
import { Api } from '../../api/api';
import type { PublicNoteView } from '../../api/types';
import { MarkdownView } from '../../components/markdown-view/markdown-view';

@Component({
  selector: 'app-public-note-page',
  imports: [MarkdownView],
  templateUrl: './public-note-page.html',
  styleUrl: './public-note-page.css',
})
export class PublicNotePage {
  private readonly api = inject(Api);

  readonly token = input<string>();

  protected readonly note = signal<PublicNoteView | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const token = this.token();
      if (!token) return;
      this.api
        .getPublicNote(token)
        .then((n) => this.note.set(n))
        .catch(() => this.error.set('This note is not available. The link may have been revoked.'));
    });
  }
}
