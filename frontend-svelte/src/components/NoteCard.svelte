<script lang="ts">
  import { api } from '../api/client';
  import type { NoteSummary } from '../api/types';
  import { extractMentionedUserIds } from '../utils/mentions';
  import { setTaskItemChecked } from '../utils/taskList';
  import { link, navigate } from '../lib/router';
  import MarkdownView from './MarkdownView.svelte';

  const PERMISSION_LABEL: Record<NoteSummary['myPermission'], string> = {
    owner: 'Owner',
    edit: 'Can edit',
    read: 'Read only',
  };

  let { note }: { note: NoteSummary } = $props();

  const href = $derived(`/notes/${note.id}`);
  // Intentionally captures only the initial value: NoteGrid keys each card
  // by `note.id` (`{#each ... (note.id)}`), so a changed id remounts this
  // component instead of updating the prop in place.
  // svelte-ignore state_referenced_locally
  let content = $state(note.contentMarkdown);
  const canEdit = $derived(note.myPermission !== 'read');

  let clampEl: HTMLDivElement | undefined = $state();
  let isClamped = $state(false);

  // The fade shadow should only show when content is actually cut off by
  // the card's max-height. Re-measured whenever the rendered content
  // changes size — including asynchronously, e.g. an image inside the
  // note finishing loading after the initial render.
  $effect(() => {
    void content;
    const clamp = clampEl;
    const rendered = clamp?.firstElementChild;
    if (!clamp || !rendered) return;

    function checkOverflow() {
      if (!clamp) return;
      isClamped = clamp.scrollHeight > clamp.clientHeight + 1;
    }

    checkOverflow();
    const observer = new ResizeObserver(checkOverflow);
    observer.observe(rendered);
    return () => observer.disconnect();
  });

  // The card renders the full note, which can itself contain <a> tags, so
  // the card can't be an <a>. The title link is the accessible navigation
  // path; this handler is a mouse-only convenience that steps aside when
  // the click lands on a link or on a text selection.
  function handleClick(e: MouseEvent) {
    if (e.defaultPrevented) return;
    if ((e.target as HTMLElement).closest('a')) return;
    if (window.getSelection()?.toString()) return;
    navigate(href);
  }

  // NoteSummary carries neither the note version nor its mentions, so a
  // checkbox toggle re-fetches the note, applies the same toggle to the
  // authoritative body, and saves that. The card updates optimistically
  // and rolls back if the write fails.
  async function handleToggleTask(index: number, checked: boolean) {
    const previous = content;
    content = setTaskItemChecked(previous, index, checked);
    try {
      const full = await api.getNote(note.id);
      const nextMarkdown = setTaskItemChecked(full.contentMarkdown, index, checked);
      const saved = await api.updateNote(note.id, full.version, {
        title: full.title,
        contentMarkdown: nextMarkdown,
        mentionedUserIds: extractMentionedUserIds(nextMarkdown),
      });
      content = saved.contentMarkdown;
    } catch {
      content = previous;
    }
  }
</script>

<!-- The whole-card click is mouse sugar only (see ADR 0007); keyboard/
     screen-reader navigation goes through the title link below, so this
     intentionally carries no role or tabindex. -->
<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div class="note-card" onclick={handleClick}>
  <h3 class="note-card-title">
    <a {href} use:link>{note.title}</a>
  </h3>
  <div class="note-card-clip" bind:this={clampEl}>
    <MarkdownView markdown={content} class="markdown-view--card" onToggleTask={canEdit ? handleToggleTask : undefined} />
    {#if isClamped}
      <div class="note-card-fade"></div>
    {/if}
  </div>
  <div class="note-card-meta">
    <span>{PERMISSION_LABEL[note.myPermission]}</span>
    {#if note.isPublic}
      <span>Public</span>
    {/if}
    <time datetime={note.updatedAt}>{new Date(note.updatedAt).toLocaleDateString()}</time>
  </div>
</div>
