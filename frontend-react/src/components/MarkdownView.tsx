import { useEffect, useMemo, useRef } from 'react';
import type { MouseEvent } from 'react';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { promoteBareTaskLines } from '../utils/taskList';

interface MarkdownViewProps {
  markdown: string;
  className?: string;
  // When provided, GFM task-list checkboxes render enabled and calling
  // back with the checkbox's document-order index (0-based) and its new
  // state lets the caller persist the change. Omitted -> checkboxes stay
  // read-only, as `marked` renders them.
  onToggleTask?: (index: number, checked: boolean) => void;
}

// Notes can be viewed by people other than their author (shares, public
// links), so rendered markdown is always sanitized before being injected
// as HTML — never trust it just because it came from our own API.
export function MarkdownView({ markdown, className, onToggleTask }: MarkdownViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  const html = useMemo(() => {
    const rawHtml = marked.parse(promoteBareTaskLines(markdown), { async: false, breaks: true });
    return DOMPurify.sanitize(rawHtml);
  }, [markdown]);

  // `marked` renders task-list checkboxes with `disabled`. Re-enable them
  // (rather than rewriting the sanitized HTML) whenever the caller wants
  // them interactive; re-runs after each content change.
  useEffect(() => {
    if (!onToggleTask || !containerRef.current) return;
    for (const box of containerRef.current.querySelectorAll<HTMLInputElement>(
      'input[type="checkbox"]',
    )) {
      box.disabled = false;
    }
  }, [html, onToggleTask]);

  function handleClick(e: MouseEvent<HTMLDivElement>) {
    if (!onToggleTask) return;
    const target = e.target as HTMLElement;
    if (!(target instanceof HTMLInputElement) || target.type !== 'checkbox') return;
    // Don't let the toggle bubble to an enclosing click handler (e.g. the
    // note card's navigate-on-click).
    e.stopPropagation();
    const boxes = containerRef.current?.querySelectorAll('input[type="checkbox"]');
    if (!boxes) return;
    const index = Array.prototype.indexOf.call(boxes, target);
    if (index === -1) return;
    onToggleTask(index, target.checked);
  }

  return (
    <div
      ref={containerRef}
      onClick={onToggleTask ? handleClick : undefined}
      className={className ? `markdown-view ${className}` : 'markdown-view'}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
