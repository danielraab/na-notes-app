// Mentions are written inline in markdown as `@[Display Name](userId)`
// (see NoteInput.contentMarkdown in /openapi/openapi.yaml) so they survive
// copy/paste and editing without a separate "mentions" data structure to
// keep in sync.

const MENTION_PATTERN = /@\[[^\]]+]\(([^)]+)\)/g;

/** All user IDs currently mentioned in markdown, de-duplicated. */
export function extractMentionedUserIds(markdown: string): string[] {
  const ids = new Set<string>();
  for (const match of markdown.matchAll(MENTION_PATTERN)) {
    ids.add(match[1]);
  }
  return [...ids];
}

export interface MentionQuery {
  query: string;
  /** Index of the '@' that starts this mention attempt. */
  start: number;
  /** Cursor position; replacing [start, end) inserts the chosen mention. */
  end: number;
}

const TRIGGER_PATTERN = /(?:^|\s)@([a-zA-Z0-9._-]{0,32})$/;

/** Finds an in-progress "@query" immediately before the cursor, if any. */
export function findMentionQuery(text: string, cursor: number): MentionQuery | null {
  const upToCursor = text.slice(0, cursor);
  const match = TRIGGER_PATTERN.exec(upToCursor);
  if (!match) return null;
  const query = match[1];
  const start = match.index + match[0].length - query.length - 1; // position of '@'
  return { query, start, end: cursor };
}

export function insertMention(text: string, mention: MentionQuery, displayName: string, userId: string): string {
  const before = text.slice(0, mention.start);
  const after = text.slice(mention.end);
  return `${before}@[${displayName}](${userId}) ${after}`;
}
