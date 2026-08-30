import { useLayoutEffect, useRef, useState } from 'react';
import type { MouseEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { NoteSummary } from '../api/types';
import { extractMentionedUserIds } from '../utils/mentions';
import { setTaskItemChecked } from '../utils/taskList';
import { MarkdownView } from './MarkdownView';

const PERMISSION_LABEL: Record<NoteSummary['myPermission'], string> = {
  owner: 'Owner',
  edit: 'Can edit',
  read: 'Read only',
};

export function NoteCard({ note }: { note: NoteSummary }) {
  const navigate = useNavigate();
  const href = `/notes/${note.id}`;
  const [content, setContent] = useState(note.contentMarkdown);
  const canEdit = note.myPermission !== 'read';

  const clampRef = useRef<HTMLDivElement>(null);
  const [isClamped, setIsClamped] = useState(false);

  // The fade shadow should only show when content is actually cut off by
  // the card's max-height. Re-measured whenever the rendered content
  // changes size — including asynchronously, e.g. an image inside the
  // note finishing loading after the initial render.
  useLayoutEffect(() => {
    const clamp = clampRef.current;
    const rendered = clamp?.firstElementChild;
    if (!clamp || !rendered) return;

    function checkOverflow() {
      if (!clamp) return;
      setIsClamped(clamp.scrollHeight > clamp.clientHeight + 1);
    }

    checkOverflow();
    const observer = new ResizeObserver(checkOverflow);
    observer.observe(rendered);
    return () => observer.disconnect();
  }, [content]);

  // The card renders the full note, which can itself contain <a> tags, so
  // the card can't be an <a>. The title link is the accessible navigation
  // path; this handler is a mouse-only convenience that steps aside when
  // the click lands on a link or on a text selection.
  function handleClick(e: MouseEvent<HTMLDivElement>) {
    if (e.defaultPrevented) return;
    if ((e.target as HTMLElement).closest('a')) return;
    if (window.getSelection()?.toString()) return;
    navigate(href);
  }

  // NoteSummary carries neither the note version nor its mentions, so a
  // checkbox toggle re-fetches the note, applies the same toggle to the
  // authoritative body, and saves that. The card updates optimistically
  // and rolls back if the write fails.
  async function handleToggleTask(index: number, checked: boolean) {
    const previous = content;
    setContent(setTaskItemChecked(previous, index, checked));
    try {
      const full = await api.getNote(note.id);
      const nextMarkdown = setTaskItemChecked(full.contentMarkdown, index, checked);
      const saved = await api.updateNote(note.id, full.version, {
        title: full.title,
        contentMarkdown: nextMarkdown,
        mentionedUserIds: extractMentionedUserIds(nextMarkdown),
      });
      setContent(saved.contentMarkdown);
    } catch {
      setContent(previous);
    }
  }

  return (
    <div
      onClick={handleClick}
      className="cursor-pointer rounded-lg border border-line bg-canvas-subtle p-4 text-fg transition-colors hover:border-accent"
    >
      <h3 className="mb-2 text-base font-semibold">
        <Link to={href} className="text-fg no-underline hover:underline">
          {note.title}
        </Link>
      </h3>
      <div ref={clampRef} className="relative max-h-96 overflow-hidden">
        <MarkdownView
          markdown={content}
          className="markdown-view--card"
          onToggleTask={canEdit ? handleToggleTask : undefined}
        />
        {isClamped && (
          <div className="pointer-events-none absolute inset-x-0 bottom-0 h-12 bg-gradient-to-b from-transparent to-canvas-subtle" />
        )}
      </div>
      <div className="mt-3 flex gap-2.5 text-xs text-fg-muted">
        <span>{PERMISSION_LABEL[note.myPermission]}</span>
        {note.isPublic && <span>Public</span>}
        <time dateTime={note.updatedAt}>{new Date(note.updatedAt).toLocaleDateString()}</time>
      </div>
    </div>
  );
}
