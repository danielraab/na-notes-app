// GFM task list checkboxes: a list item whose text starts with `[ ]` or
// `[x]`. `marked` renders these as <input type="checkbox">; this module
// maps a rendered checkbox (by its document-order index) back to its
// source line so it can be toggled in place, without round-tripping the
// whole note through a Markdown serializer.

const TASK_LINE = /^(\s*(?:[-*+]|\d+[.)])\s+)\[([ xX])\](\s)/;
const FENCE = /^\s*(?:```|~~~)/;

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
