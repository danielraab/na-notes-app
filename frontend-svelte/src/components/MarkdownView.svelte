<script lang="ts">
  import { marked } from 'marked';
  import DOMPurify from 'dompurify';
  import { normalizeTaskLines } from '../utils/taskList';

  interface Props {
    markdown: string;
    class?: string;
    // When provided, GFM task-list checkboxes render enabled and calling
    // back with the checkbox's document-order index (0-based) and its new
    // state lets the caller persist the change. Omitted -> checkboxes stay
    // read-only, as `marked` renders them.
    onToggleTask?: (index: number, checked: boolean) => void;
  }

  let { markdown, class: className, onToggleTask }: Props = $props();

  let containerEl: HTMLDivElement | undefined = $state();

  // Notes can be viewed by people other than their author (shares, public
  // links), so rendered markdown is always sanitized before being injected
  // as HTML — never trust it just because it came from our own API.
  let html = $derived.by(() => {
    const rawHtml = marked.parse(normalizeTaskLines(markdown), { async: false, breaks: true });
    return DOMPurify.sanitize(rawHtml);
  });

  // `marked` renders task-list checkboxes with `disabled`. Re-enable them
  // (rather than rewriting the sanitized HTML) whenever the caller wants
  // them interactive; re-runs after each content change.
  $effect(() => {
    void html;
    if (!onToggleTask || !containerEl) return;
    for (const box of containerEl.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')) {
      box.disabled = false;
    }
  });

  function handleClick(event: MouseEvent) {
    if (!onToggleTask) return;
    const target = event.target as HTMLElement;
    if (!(target instanceof HTMLInputElement) || target.type !== 'checkbox') return;
    // Don't let the toggle bubble to an enclosing click handler (e.g. the
    // note card's navigate-on-click).
    event.stopPropagation();
    const boxes = containerEl?.querySelectorAll('input[type="checkbox"]');
    if (!boxes) return;
    const index = Array.prototype.indexOf.call(boxes, target);
    if (index === -1) return;
    onToggleTask(index, target.checked);
  }
</script>

<!-- The click handler only ever acts on a checkbox inside the sanitized,
     rendered markdown (see handleClick above) — the container itself has
     no interactive semantics of its own. -->
<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  bind:this={containerEl}
  onclick={onToggleTask ? handleClick : undefined}
  class={className ? `markdown-view ${className}` : 'markdown-view'}
>
  {@html html}
</div>
