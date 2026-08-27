import type { MouseEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { NoteSummary } from '../api/types';
import { MarkdownView } from './MarkdownView';

const PERMISSION_LABEL: Record<NoteSummary['myPermission'], string> = {
  owner: 'Owner',
  edit: 'Can edit',
  read: 'Read only',
};

export function NoteCard({ note }: { note: NoteSummary }) {
  const navigate = useNavigate();
  const href = `/notes/${note.id}`;

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
      <div className="relative max-h-56 overflow-hidden">
        <MarkdownView markdown={note.contentMarkdown} className="markdown-view--card" />
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-12 bg-gradient-to-b from-transparent to-canvas-subtle" />
      </div>
      <div className="mt-3 flex gap-2.5 text-xs text-fg-muted">
        <span>{PERMISSION_LABEL[note.myPermission]}</span>
        {note.isPublic && <span>Public</span>}
        <time dateTime={note.updatedAt}>{new Date(note.updatedAt).toLocaleDateString()}</time>
      </div>
    </div>
  );
}
