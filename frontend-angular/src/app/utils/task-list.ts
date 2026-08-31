// GFM task list checkboxes: `marked` only renders `[ ]` / `[x]` as an
// <input type="checkbox"> when it is the start of a list item *and*
// spelled exactly. Notes in the wild also write bare `[ ] thing` lines
// with no `-`, and empty `[]` for "unchecked". This module normalizes
// those for rendering and maps a rendered checkbox (by its document-order
// index) back to its source line so it can be toggled in place.

// A task line: leading indent (1), an optional list marker with its
// trailing space (2), a checkbox token `[]` / `[ ]` / `[x]` / `[X]` whose
// check char is group 3, then the run of space before the text (4).
// `(?=\S)` requires actual content after the box, matching `marked`.
const TASK_LINE = /^([ \t]*)((?:[-*+]|\d+[.)])[ \t]+)?\[([ xX]?)\]([ \t]+)(?=\S)/;
const FENCE = /^\s*(?:```|~~~)/;

function isChecked(mark: string): boolean {
  return mark === 'x' || mark === 'X';
}

/**
 * Rewrites task lines to the exact spelling `marked` needs: `[]` and
 * `[ ]` become `- [ ]`, `[x]` becomes `- [x]`, and a bare line with no
 * list marker gains a `- `. Fenced code, real list items, and inline
 * `[ ]` are left alone.
 */
export function normalizeTaskLines(markdown: string): string {
  const lines = markdown.split('\n');
  let inFence = false;
  for (let i = 0; i < lines.length; i++) {
    if (FENCE.test(lines[i])) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;
    lines[i] = lines[i].replace(
      TASK_LINE,
      (_m, indent: string, marker: string | undefined, mark: string, space: string) =>
        `${indent}${marker ?? '- '}[${isChecked(mark) ? 'x' : ' '}]${space}`,
    );
  }
  return lines.join('\n');
}

/** Runs `fn` for each task-list line, numbered in document order. Lines
 *  inside fenced code blocks are skipped, matching what `marked` renders. */
function forEachTaskLine(
  markdown: string,
  fn: (lineIndex: number, taskIndex: number) => void,
): void {
  const lines = markdown.split('\n');
  let inFence = false;
  let taskIndex = 0;
  for (let i = 0; i < lines.length; i++) {
    if (FENCE.test(lines[i])) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;
    if (TASK_LINE.test(lines[i])) {
      fn(i, taskIndex);
      taskIndex++;
    }
  }
}

/** Number of rendered task checkboxes in `markdown`. */
export function countTaskItems(markdown: string): number {
  let count = 0;
  forEachTaskLine(markdown, () => {
    count++;
  });
  return count;
}

/**
 * Returns `markdown` with the `index`-th task checkbox (0-based, document
 * order) set to `checked`. The touched line keeps its own indentation and
 * list marker (or lack of one); an empty `[]` becomes `[ ]` / `[x]`.
 * Returns the input unchanged if there is no such checkbox.
 */
export function setTaskItemChecked(markdown: string, index: number, checked: boolean): string {
  const lines = markdown.split('\n');
  forEachTaskLine(markdown, (lineIndex, taskIndex) => {
    if (taskIndex !== index) return;
    lines[lineIndex] = lines[lineIndex].replace(
      TASK_LINE,
      (_m, indent: string, marker: string | undefined, _mark: string, space: string) =>
        `${indent}${marker ?? ''}[${checked ? 'x' : ' '}]${space}`,
    );
  });
  return lines.join('\n');
}
