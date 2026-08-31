import { Component, ElementRef, OnDestroy, afterNextRender, inject, signal, viewChild } from '@angular/core';
import { Api } from '../../api/api';
import type { NoteSummary } from '../../api/types';
import { NoteCard } from '../note-card/note-card';

// Backs the dashboard's cursor-paginated "load more on scroll" feed (ADR
// 0007). The cursor itself is opaque — this component only ever passes
// back the nextCursor it was given, never inspects or constructs one.
@Component({
  selector: 'app-note-grid',
  imports: [NoteCard],
  templateUrl: './note-grid.html',
  styleUrl: './note-grid.css',
})
export class NoteGrid implements OnDestroy {
  private readonly api = inject(Api);

  protected readonly notes = signal<NoteSummary[]>([]);
  protected readonly hasMore = signal(true);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  private cursor: string | null = null;
  private loadingInFlight = false;
  private observer?: IntersectionObserver;
  private readonly sentinel = viewChild.required<ElementRef<HTMLDivElement>>('sentinel');

  constructor() {
    this.loadMore();

    afterNextRender(() => {
      this.observer = new IntersectionObserver(
        (entries) => {
          if (entries[0]?.isIntersecting) this.loadMore();
        },
        { rootMargin: '200px' },
      );
      this.observer.observe(this.sentinel().nativeElement);
    });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  protected async loadMore(): Promise<void> {
    if (this.loadingInFlight || !this.hasMore()) return;
    this.loadingInFlight = true;
    this.loading.set(true);
    this.error.set(null);
    try {
      const page = await this.api.listNotes(this.cursor);
      this.cursor = page.nextCursor;
      this.notes.update((prev) => [...prev, ...page.items]);
      this.hasMore.set(page.nextCursor !== null);
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'Failed to load notes');
    } finally {
      this.loadingInFlight = false;
      this.loading.set(false);
    }
  }
}
