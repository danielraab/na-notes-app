import { describe, expect, it } from 'vitest';
import { countTaskItems, promoteBareTaskLines, setTaskItemChecked } from './taskList';

const NOTE = ['# Groceries', '', '- [ ] milk', '- [x] eggs', '- [ ] bread', '', 'plain line'].join(
  '\n',
);

describe('promoteBareTaskLines', () => {
  it('adds a list marker to bare `[ ]` / `[x]` lines', () => {
    expect(promoteBareTaskLines('[ ] milk\n[x] eggs')).toBe('- [ ] milk\n- [x] eggs');
  });

  it('keeps indentation and leaves list items and prose alone', () => {
    expect(promoteBareTaskLines('  [ ] sub')).toBe('  - [ ] sub');
    expect(promoteBareTaskLines('- [ ] already')).toBe('- [ ] already');
    expect(promoteBareTaskLines('text [ ] inline')).toBe('text [ ] inline');
  });

  it('does not touch checkbox-looking lines inside fenced code', () => {
    expect(promoteBareTaskLines('```\n[ ] not real\n```\n[ ] real')).toBe(
      '```\n[ ] not real\n```\n- [ ] real',
    );
  });
});

describe('countTaskItems', () => {
  it('counts task-list checkboxes, with or without a list marker', () => {
    expect(countTaskItems(NOTE)).toBe(3);
    expect(countTaskItems('[ ] a\n[x] b')).toBe(2);
    expect(countTaskItems('- a\n- b')).toBe(0);
  });

  it('supports `*`, `+` and ordered markers', () => {
    expect(countTaskItems('* [ ] a\n+ [x] b\n1. [ ] c\n2) [x] d')).toBe(4);
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

  it('toggles bare `[ ]` lines that have no list marker', () => {
    expect(setTaskItemChecked('[ ] a\n[x] b', 0, true)).toBe('[x] a\n[x] b');
    expect(setTaskItemChecked('[ ] a\n[x] b', 1, false)).toBe('[ ] a\n[ ] b');
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
