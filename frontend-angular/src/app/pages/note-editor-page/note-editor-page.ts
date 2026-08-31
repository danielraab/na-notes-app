import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api, VersionConflictError } from '../../api/api';
import type { Note } from '../../api/types';
import { Auth } from '../../auth/auth';
import { MarkdownEditor } from '../../components/markdown-editor/markdown-editor';
import { MarkdownView } from '../../components/markdown-view/markdown-view';
import { ShareDialog } from '../../components/share-dialog/share-dialog';
import { extractMentionedUserIds } from '../../utils/mentions';

// `id` is bound straight from the `:id` route param via the router's
// component-input-binding feature (see app.routes.ts / app.config.ts) —
// undefined on `/notes/new`, which is how this page tells "new" from
// "edit" apart, same as frontend-react's `useParams` check.
@Component({
  selector: 'app-note-editor-page',
  imports: [MarkdownEditor, MarkdownView, ShareDialog],
  templateUrl: './note-editor-page.html',
  styleUrl: './note-editor-page.css',
})
export class NoteEditorPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly auth = inject(Auth);

  readonly id = input<string>();
  protected readonly isNew = computed(() => this.id() === undefined);

  protected readonly note = signal<Note | null>(null);
  protected readonly title = signal('');
  protected readonly content = signal('');
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly justSaved = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly conflict = signal<Note | null>(null);
  protected readonly showShare = signal(false);

  protected readonly readOnly = computed(() => this.note()?.myPermission === 'read');
  protected readonly canManageSharing = computed(() => this.note()?.myPermission === 'owner');
  protected readonly canDelete = computed(() => this.note()?.myPermission === 'owner');

  constructor() {
    effect(() => {
      const id = this.id();
      if (id !== undefined) this.loadNote(id);
    });

    effect((onCleanup) => {
      if (!this.justSaved()) return;
      const timer = setTimeout(() => this.justSaved.set(false), 3000);
      onCleanup(() => clearTimeout(timer));
    });
  }

  private async loadNote(id: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const n = await this.api.getNote(id);
      this.note.set(n);
      this.title.set(n.title);
      this.content.set(n.contentMarkdown);
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'Failed to load note');
    } finally {
      this.loading.set(false);
    }
  }

  protected onTitleInput(value: string): void {
    this.title.set(value);
    this.justSaved.set(false);
  }

  protected onContentChange(value: string): void {
    this.content.set(value);
    this.justSaved.set(false);
  }

  protected async handleSave(): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    this.conflict.set(null);
    this.justSaved.set(false);
    const mentionedUserIds = extractMentionedUserIds(this.content());
    try {
      if (this.isNew()) {
        const created = await this.api.createNote({
          title: this.title(),
          contentMarkdown: this.content(),
          mentionedUserIds,
        });
        this.justSaved.set(true);
        this.router.navigate(['/notes', created.id], { replaceUrl: true });
      } else {
        const current = this.note();
        if (!current) return;
        const updated = await this.api.updateNote(current.id, current.version, {
          title: this.title(),
          contentMarkdown: this.content(),
          mentionedUserIds,
        });
        this.note.set(updated);
        this.justSaved.set(true);
      }
    } catch (err) {
      if (err instanceof VersionConflictError) {
        this.conflict.set(err.currentNote);
      } else {
        this.error.set(err instanceof Error ? err.message : 'Failed to save note');
      }
    } finally {
      this.saving.set(false);
    }
  }

  protected async handleDelete(): Promise<void> {
    const current = this.note();
    if (!current || !window.confirm('Delete this note? This cannot be undone.')) return;
    await this.api.deleteNote(current.id);
    this.router.navigateByUrl('/');
  }

  protected reloadLatest(): void {
    const latest = this.conflict();
    if (!latest) return;
    this.note.set(latest);
    this.title.set(latest.title);
    this.content.set(latest.contentMarkdown);
    this.conflict.set(null);
  }

  protected login(): void {
    this.auth.login(window.location.pathname);
  }
}
