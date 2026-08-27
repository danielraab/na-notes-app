import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, KeyboardEvent } from 'react';
import { api } from '../api/client';
import type { UserSummary } from '../api/types';
import { findMentionQuery, insertMention } from '../utils/mentions';
import type { MentionQuery } from '../utils/mentions';
import { MarkdownView } from './MarkdownView';

interface Props {
  value: string;
  onChange: (value: string) => void;
}

// Always-Markdown source of truth (per product spec, rich text is
// optional and out of scope here): a plain textarea with a live preview
// tab, plus "@" mention autocomplete backed by /api/users/search.
export function MarkdownEditor({ value, onChange }: Props) {
  const [showPreview, setShowPreview] = useState(false);
  const [mentionQuery, setMentionQuery] = useState<MentionQuery | null>(null);
  const [suggestions, setSuggestions] = useState<UserSummary[]>([]);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!mentionQuery) {
      setSuggestions([]);
      return;
    }
    const timeoutId = setTimeout(() => {
      api
        .searchUsers(mentionQuery.query)
        .then(setSuggestions)
        .catch(() => setSuggestions([]));
    }, 200);
    return () => clearTimeout(timeoutId);
  }, [mentionQuery]);

  function handleChange(e: ChangeEvent<HTMLTextAreaElement>) {
    const next = e.target.value;
    onChange(next);
    setMentionQuery(findMentionQuery(next, e.target.selectionStart));
  }

  function handleKeyUp() {
    const el = textareaRef.current;
    if (!el) return;
    setMentionQuery(findMentionQuery(value, el.selectionStart));
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (mentionQuery && suggestions.length > 0 && e.key === 'Escape') {
      setMentionQuery(null);
    }
  }

  function selectMention(user: UserSummary) {
    if (!mentionQuery) return;
    onChange(insertMention(value, mentionQuery, user.displayName, user.id));
    setMentionQuery(null);
    textareaRef.current?.focus();
  }

  return (
    <div>
      <div className="mb-2 flex gap-1">
        <button
          type="button"
          className={!showPreview ? 'btn btn-primary' : 'btn'}
          onClick={() => setShowPreview(false)}
        >
          Write
        </button>
        <button
          type="button"
          className={showPreview ? 'btn btn-primary' : 'btn'}
          onClick={() => setShowPreview(true)}
        >
          Preview
        </button>
      </div>

      {showPreview ? (
        <MarkdownView markdown={value} />
      ) : (
        <div className="relative">
          <textarea
            ref={textareaRef}
            className="w-full resize-y rounded-md border border-line bg-canvas p-3 font-mono text-sm text-fg outline-none focus:border-accent focus:ring-2 focus:ring-accent/30"
            value={value}
            onChange={handleChange}
            onKeyUp={handleKeyUp}
            onKeyDown={handleKeyDown}
            onClick={handleKeyUp}
            placeholder="Write your note in Markdown... type @ to mention someone"
            rows={16}
          />
          {mentionQuery && suggestions.length > 0 && (
            <ul className="absolute z-10 mt-1 max-w-[260px] list-none rounded-md border border-line bg-canvas p-1">
              {suggestions.map((user) => (
                <li key={user.id}>
                  <button
                    type="button"
                    className="block w-full rounded px-2 py-1 text-left text-sm hover:bg-canvas-subtle"
                    onClick={() => selectMention(user)}
                  >
                    {user.displayName}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
