import { Component, ElementRef, inject, model, signal, viewChild } from '@angular/core';
import { Api } from '../../api/api';
import type { UserSummary } from '../../api/types';
import { type MentionQuery, findMentionQuery, insertMention } from '../../utils/mentions';
import { MarkdownView } from '../markdown-view/markdown-view';

// Always-Markdown source of truth (per product spec, rich text is
// optional and out of scope here): a plain textarea with a live preview
// tab, plus "@" mention autocomplete backed by /api/users/search.
@Component({
  selector: 'app-markdown-editor',
  imports: [MarkdownView],
  templateUrl: './markdown-editor.html',
  styleUrl: './markdown-editor.css',
})
export class MarkdownEditor {
  private readonly api = inject(Api);

  readonly value = model.required<string>();

  protected readonly showPreview = signal(false);
  protected readonly mentionQuery = signal<MentionQuery | null>(null);
  protected readonly suggestions = signal<UserSummary[]>([]);

  private readonly textarea = viewChild<ElementRef<HTMLTextAreaElement>>('textarea');

  protected handleInput(event: Event): void {
    const el = event.target as HTMLTextAreaElement;
    this.value.set(el.value);
    this.mentionQuery.set(findMentionQuery(el.value, el.selectionStart));
    this.loadSuggestions();
  }

  protected handleCaretMove(): void {
    const el = this.textarea()?.nativeElement;
    if (!el) return;
    this.mentionQuery.set(findMentionQuery(this.value(), el.selectionStart));
    this.loadSuggestions();
  }

  protected handleKeyDown(event: KeyboardEvent): void {
    if (this.mentionQuery() && this.suggestions().length > 0 && event.key === 'Escape') {
      this.mentionQuery.set(null);
    }
  }

  private searchToken = 0;
  private loadSuggestions(): void {
    const query = this.mentionQuery();
    if (!query) {
      this.suggestions.set([]);
      return;
    }
    const token = ++this.searchToken;
    setTimeout(() => {
      if (token !== this.searchToken) return; // superseded by a later keystroke
      this.api
        .searchUsers(query.query)
        .then((results) => {
          if (token === this.searchToken) this.suggestions.set(results);
        })
        .catch(() => {
          if (token === this.searchToken) this.suggestions.set([]);
        });
    }, 200);
  }

  protected selectMention(user: UserSummary): void {
    const query = this.mentionQuery();
    if (!query) return;
    this.value.set(insertMention(this.value(), query, user.displayName, user.id));
    this.mentionQuery.set(null);
    this.textarea()?.nativeElement.focus();
  }
}
