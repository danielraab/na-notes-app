import { countTaskItems, normalizeTaskLines, setTaskItemChecked } from './task-list';

const NOTE = ['# Groceries', '', '- [ ] milk', '- [x] eggs', '- [ ] bread', '', 'plain line'].join(
  '\n',
);

describe('normalizeTaskLines', () => {
  it('promotes bare `[ ]` / `[x]` lines to list items', () => {
    expect(normalizeTaskLines('[ ] milk\n[x] eggs')).toBe('- [ ] milk\n- [x] eggs');
  });

  it('treats empty `[]` as an unchecked box', () => {
    expect(normalizeTaskLines('[] milk')).toBe('- [ ] milk');
    expect(normalizeTaskLines('- [] milk')).toBe('- [ ] milk');
  });

  it('keeps indentation, list markers and prose alone', () => {
    expect(normalizeTaskLines('  [] sub')).toBe('  - [ ] sub');
    expect(normalizeTaskLines('- [ ] already')).toBe('- [ ] already');
    expect(normalizeTaskLines('text [ ] inline')).toBe('text [ ] inline');
    expect(normalizeTaskLines('[x]: https://example.com')).toBe('[x]: https://example.com');
  });

  it('does not touch checkbox-looking lines inside fenced code', () => {
    expect(normalizeTaskLines('```\n[] not real\n```\n[] real')).toBe(
      '```\n[] not real\n```\n- [ ] real',
    );
  });
});

describe('countTaskItems', () => {
  it('counts task checkboxes in every accepted spelling', () => {
    expect(countTaskItems(NOTE)).toBe(3);
    expect(countTaskItems('[ ] a\n[x] b\n[] c')).toBe(3);
    expect(countTaskItems('- a\n- b')).toBe(0);
  });

  it('supports `*`, `+` and ordered markers', () => {
    expect(countTaskItems('* [ ] a\n+ [x] b\n1. [ ] c\n2) [] d')).toBe(4);
  });

  it('ignores checkboxes inside fenced code blocks', () => {
    expect(countTaskItems('```\n- [ ] not real\n```\n- [ ] real')).toBe(1);
  });
});

describe('setTaskItemChecked', () => {
  it('checks the nth unchecked box, preserving indentation and text', () => {
    expect(setTaskItemChecked(NOTE, 0, true)).toContain('- [x] milk');
    expect(setTaskItemChecked(NOTE, 2, true)).toContain('- [x] bread');
  });

  it('unchecks a checked box', () => {
    expect(setTaskItemChecked(NOTE, 1, false)).toContain('- [ ] eggs');
  });

  it('toggles bare `[ ]` lines without adding a list marker', () => {
    expect(setTaskItemChecked('[ ] a\n[x] b', 0, true)).toBe('[x] a\n[x] b');
    expect(setTaskItemChecked('[ ] a\n[x] b', 1, false)).toBe('[ ] a\n[ ] b');
  });

  it('canonicalises an empty `[]` when toggled', () => {
    expect(setTaskItemChecked('[] a', 0, true)).toBe('[x] a');
    expect(setTaskItemChecked('- [] a', 0, false)).toBe('- [ ] a');
  });

  it('touches only the targeted line', () => {
    const out = setTaskItemChecked(NOTE, 2, true).split('\n');
    expect(out[2]).toBe('- [ ] milk');
    expect(out[3]).toBe('- [x] eggs');
    expect(out[4]).toBe('- [x] bread');
  });

  it('keeps nested indentation', () => {
    expect(setTaskItemChecked('  - [ ] sub', 0, true)).toBe('  - [x] sub');
  });

  it('returns the input unchanged when the index is out of range', () => {
    expect(setTaskItemChecked(NOTE, 9, true)).toBe(NOTE);
  });

  it('counts past fenced code blocks when indexing', () => {
    const md = '```\n- [ ] fake\n```\n- [ ] a\n- [ ] b';
    expect(setTaskItemChecked(md, 1, true)).toBe('```\n- [ ] fake\n```\n- [ ] a\n- [x] b');
  });
});
