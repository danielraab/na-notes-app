import { extractMentionedUserIds, findMentionQuery, insertMention } from './mentions';

describe('extractMentionedUserIds', () => {
  it('finds all mentions and de-duplicates', () => {
    const md = 'Hi @[Alice](user-1) and @[Bob](user-2), also @[Alice again](user-1)';
    expect(extractMentionedUserIds(md).sort()).toEqual(['user-1', 'user-2']);
  });

  it('returns an empty array when there are no mentions', () => {
    expect(extractMentionedUserIds('just plain markdown')).toEqual([]);
  });
});

describe('findMentionQuery', () => {
  it('matches an in-progress mention right before the cursor', () => {
    const text = 'hello @ali';
    const match = findMentionQuery(text, text.length);
    expect(match).toEqual({ query: 'ali', start: 6, end: 10 });
  });

  it('does not match when the cursor is not immediately after the trigger', () => {
    const text = '@alice said hi';
    expect(findMentionQuery(text, text.length)).toBeNull();
  });

  it('does not match a bare word without an @ prefix', () => {
    expect(findMentionQuery('hello alice', 11)).toBeNull();
  });
});

describe('insertMention', () => {
  it('replaces the query with a mention token and trailing space', () => {
    const text = 'hello @ali there';
    const match = findMentionQuery(text, 10)!;
    expect(insertMention(text, match, 'Alice', 'user-1')).toBe('hello @[Alice](user-1)  there');
  });
});
