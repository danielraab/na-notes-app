// GFM task list checkboxes: `marked` only renders `[ ]` / `[x]` as an
// <input type="checkbox"> when it is the start of a list item. Notes in
// the wild also write bare `[ ] thing` lines with no `-`, so this module
// both promotes those to list items for rendering and maps a rendered
// checkbox (by its document-order index) back to its source line so it
// can be toggled in place.

// A task line, with or without a leading list marker (`-`, `*`, `+`,
// `1.`, `1)`). Group 1 is everything up to and including the `[`, group 2
// the check mark, group 3 the whitespace that must follow the `]`.
const TASK_LINE = /^(\s*(?:[-*+]|\d+[.)])?[ \t]*)\[([ xX])\]([ \t])/;
const BARE_TASK_LINE = /^(\s*)(\[[ xX]\][ \t])/;
const FENCE = /^\s*(?:```|~~~)/;

/**
 * Promotes bare `[ ] thing` lines (no list marker) to `- [ ] thing` so
 * `marked` renders them as task-list checkboxes. Lines already in a list
 * and lines inside fenced code blocks are left alone.
 */
export function promoteBareTaskLines(markdown: string): string {
  const lines = markdown.split('\n');
  let inFence = false;
  for (let i = 0; i < lines.length; i++) {
    if (FENCE.test(lines[i])) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;
    lines[i] = lines[i].replace(BARE_TASK_LINE, '$1- $2');
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
 * order) set to `checked`. Returns the input unchanged if there is no
 * such checkbox.
 */
export function setTaskItemChecked(markdown: string, index: number, checked: boolean): string {
  const lines = markdown.split('\n');
  forEachTaskLine(markdown, (lineIndex, taskIndex) => {
    if (taskIndex !== index) return;
    lines[lineIndex] = lines[lineIndex].replace(
      TASK_LINE,
      (_m, prefix: string, _mark: string, space: string) =>
        `${prefix}[${checked ? 'x' : ' '}]${space}`,
    );
  });
  return lines.join('\n');
}
