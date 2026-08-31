import { Component, ElementRef, afterRenderEffect, computed, inject, input, output, viewChild } from '@angular/core';
import { DomSanitizer, type SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { normalizeTaskLines } from '../../utils/task-list';

export interface TaskToggle {
  index: number;
  checked: boolean;
}

// Notes can be viewed by people other than their author (shares, public
// links), so rendered markdown is always sanitized before being injected
// as HTML — never trust it just because it came from our own API. Every
// markdown render path in this app goes through this component.
@Component({
  selector: 'app-markdown-view',
  templateUrl: './markdown-view.html',
})
export class MarkdownView {
  readonly markdown = input.required<string>();
  readonly variant = input<'default' | 'card'>('default');
  // When true, GFM task-list checkboxes render enabled and clicking one
  // emits `toggleTask` with its document-order index (0-based) and new
  // state, so the caller can persist the change. False (default) leaves
  // checkboxes `disabled`, exactly as `marked` renders them.
  readonly interactive = input(false);
  readonly toggleTask = output<TaskToggle>();

  private readonly sanitizer = inject(DomSanitizer);
  private readonly container = viewChild.required<ElementRef<HTMLDivElement>>('container');

  // DOMPurify has already sanitized this HTML (the only sanitization step
  // this app trusts — see docs/decisions/0004-markdown-rendering.md), so
  // it's explicitly marked safe for Angular's own `[innerHTML]` sanitizer
  // to pass through unchanged. Angular's default sanitizer would otherwise
  // additionally strip interactive elements like the task-list
  // `<input type="checkbox">` it renders, which this view depends on.
  protected readonly html = computed<SafeHtml>(() => {
    const rawHtml = marked.parse(normalizeTaskLines(this.markdown()), { async: false, breaks: true }) as string;
    return this.sanitizer.bypassSecurityTrustHtml(DOMPurify.sanitize(rawHtml));
  });

  constructor() {
    // `marked` renders task-list checkboxes with `disabled`. Re-enable
    // them (rather than rewriting the sanitized HTML) whenever this view
    // is interactive; re-runs after every content change.
    afterRenderEffect(() => {
      this.html();
      if (!this.interactive()) return;
      for (const box of this.container().nativeElement.querySelectorAll<HTMLInputElement>(
        'input[type="checkbox"]',
      )) {
        box.disabled = false;
      }
    });
  }

  protected handleClick(event: MouseEvent): void {
    if (!this.interactive()) return;
    const target = event.target as HTMLElement;
    if (!(target instanceof HTMLInputElement) || target.type !== 'checkbox') return;
    // Don't let the toggle bubble to an enclosing click handler (e.g. the
    // note card's navigate-on-click).
    event.stopPropagation();
    const boxes = Array.from(this.container().nativeElement.querySelectorAll('input[type="checkbox"]'));
    const index = boxes.indexOf(target);
    if (index === -1) return;
    this.toggleTask.emit({ index, checked: target.checked });
  }
}
