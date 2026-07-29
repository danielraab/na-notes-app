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
    <div className="markdown-editor">
      <div className="markdown-editor__tabs">
        <button type="button" className={!showPreview ? 'active' : ''} onClick={() => setShowPreview(false)}>
          Write
        </button>
        <button type="button" className={showPreview ? 'active' : ''} onClick={() => setShowPreview(true)}>
          Preview
        </button>
      </div>

      {showPreview ? (
        <MarkdownView markdown={value} />
      ) : (
        <div className="markdown-editor__input-wrapper">
          <textarea
            ref={textareaRef}
            className="markdown-editor__textarea"
            value={value}
            onChange={handleChange}
            onKeyUp={handleKeyUp}
            onKeyDown={handleKeyDown}
            onClick={handleKeyUp}
            placeholder="Write your note in Markdown... type @ to mention someone"
            rows={16}
          />
          {mentionQuery && suggestions.length > 0 && (
            <ul className="mention-suggestions">
              {suggestions.map((user) => (
                <li key={user.id}>
                  <button type="button" onClick={() => selectMention(user)}>
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
