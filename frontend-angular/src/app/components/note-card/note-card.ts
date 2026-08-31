import { DatePipe } from '@angular/common';
import {
  Component,
  ElementRef,
  OnDestroy,
  afterRenderEffect,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import type { NoteSummary, Permission } from '../../api/types';
import { extractMentionedUserIds } from '../../utils/mentions';
import { setTaskItemChecked } from '../../utils/task-list';
import { MarkdownView, type TaskToggle } from '../markdown-view/markdown-view';

const PERMISSION_LABEL: Record<Permission, string> = {
  owner: 'Owner',
  edit: 'Can edit',
  read: 'Read only',
};

@Component({
  selector: 'app-note-card',
  imports: [RouterLink, DatePipe, MarkdownView],
  templateUrl: './note-card.html',
  styleUrl: './note-card.css',
})
export class NoteCard implements OnDestroy {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  readonly note = input.required<NoteSummary>();

  // A local, optimistically-editable copy of the note body that resets
  // whenever a new `note` input arrives (e.g. this card gets recycled for
  // a different page of results).
  protected readonly content = linkedSignal(() => this.note().contentMarkdown);
  protected readonly canEdit = computed(() => this.note().myPermission !== 'read');
  protected readonly permissionLabel = computed(() => PERMISSION_LABEL[this.note().myPermission]);

  protected readonly isClamped = signal(false);
  private readonly clamp = viewChild.required<ElementRef<HTMLDivElement>>('clamp');
  private resizeObserver?: ResizeObserver;

  constructor() {
    // The fade shadow should only show when content is actually cut off
    // by the card's max-height. Re-measured whenever the rendered content
    // changes size — including asynchronously, e.g. an image inside the
    // note finishing loading after the initial render.
    afterRenderEffect(() => {
      this.content();
      const clampEl = this.clamp().nativeElement;
      const rendered = clampEl.firstElementChild;
      if (!rendered) return;

      const checkOverflow = () => this.isClamped.set(clampEl.scrollHeight > clampEl.clientHeight + 1);
      checkOverflow();

      this.resizeObserver?.disconnect();
      this.resizeObserver = new ResizeObserver(checkOverflow);
      this.resizeObserver.observe(rendered);
    });
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }

  // The card renders the full note, which can itself contain <a> tags, so
  // the card can't be an <a>. The title link is the accessible navigation
  // path; this handler is a mouse-only convenience that steps aside when
  // the click lands on a link or on a text selection.
  protected onCardClick(event: MouseEvent): void {
    if (event.defaultPrevented) return;
    if ((event.target as HTMLElement).closest('a')) return;
    if (window.getSelection()?.toString()) return;
    this.router.navigate(['/notes', this.note().id]);
  }

  // NoteSummary carries neither the note version nor its mentions, so a
  // checkbox toggle re-fetches the note, applies the same toggle to the
  // authoritative body, and saves that. The card updates optimistically
  // and rolls back if the write fails.
  protected async onToggleTask({ index, checked }: TaskToggle): Promise<void> {
    const previous = this.content();
    this.content.set(setTaskItemChecked(previous, index, checked));
    try {
      const full = await this.api.getNote(this.note().id);
      const nextMarkdown = setTaskItemChecked(full.contentMarkdown, index, checked);
      const saved = await this.api.updateNote(full.id, full.version, {
        title: full.title,
        contentMarkdown: nextMarkdown,
        mentionedUserIds: extractMentionedUserIds(nextMarkdown),
      });
      this.content.set(saved.contentMarkdown);
    } catch {
      this.content.set(previous);
    }
  }
}
