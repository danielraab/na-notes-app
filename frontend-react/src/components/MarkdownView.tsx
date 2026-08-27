import { useMemo } from 'react';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

// Notes can be viewed by people other than their author (shares, public
// links), so rendered markdown is always sanitized before being injected
// as HTML — never trust it just because it came from our own API.
export function MarkdownView({ markdown, className }: { markdown: string; className?: string }) {
  const html = useMemo(() => {
    const rawHtml = marked.parse(markdown, { async: false, breaks: true });
    return DOMPurify.sanitize(rawHtml);
  }, [markdown]);

  return (
    <div
      className={className ? `markdown-view ${className}` : 'markdown-view'}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
