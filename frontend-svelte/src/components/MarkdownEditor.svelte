<script lang="ts">
  import { api } from '../api/client';
  import type { UserSummary } from '../api/types';
  import { findMentionQuery, insertMention } from '../utils/mentions';
  import type { MentionQuery } from '../utils/mentions';
  import MarkdownView from './MarkdownView.svelte';

  interface Props {
    value: string;
    onChange: (value: string) => void;
  }

  let { value, onChange }: Props = $props();

  let showPreview = $state(false);
  let mentionQuery = $state<MentionQuery | null>(null);
  let suggestions = $state<UserSummary[]>([]);
  let textareaEl: HTMLTextAreaElement | undefined = $state();

  $effect(() => {
    if (!mentionQuery) {
      suggestions = [];
      return;
    }
    const query = mentionQuery.query;
    const timeoutId = setTimeout(() => {
      api
        .searchUsers(query)
        .then((results) => (suggestions = results))
        .catch(() => (suggestions = []));
    }, 200);
    return () => clearTimeout(timeoutId);
  });

  function handleInput(e: Event) {
    const target = e.currentTarget as HTMLTextAreaElement;
    onChange(target.value);
    mentionQuery = findMentionQuery(target.value, target.selectionStart);
  }

  function handleKeyUp() {
    if (!textareaEl) return;
    mentionQuery = findMentionQuery(value, textareaEl.selectionStart);
  }

  function handleKeyDown(e: KeyboardEvent) {
    if (mentionQuery && suggestions.length > 0 && e.key === 'Escape') {
      mentionQuery = null;
    }
  }

  function selectMention(user: UserSummary) {
    if (!mentionQuery) return;
    onChange(insertMention(value, mentionQuery, user.displayName, user.id));
    mentionQuery = null;
    textareaEl?.focus();
  }
</script>

<div class="editor-tabs">
  <button type="button" class={!showPreview ? 'btn btn-primary' : 'btn'} onclick={() => (showPreview = false)}>
    Write
  </button>
  <button type="button" class={showPreview ? 'btn btn-primary' : 'btn'} onclick={() => (showPreview = true)}>
    Preview
  </button>
</div>

{#if showPreview}
  <MarkdownView markdown={value} />
{:else}
  <div class="editor-textarea-wrap">
    <textarea
      bind:this={textareaEl}
      class="editor-textarea"
      {value}
      oninput={handleInput}
      onkeyup={handleKeyUp}
      onkeydown={handleKeyDown}
      onclick={handleKeyUp}
      placeholder="Write your note in Markdown... type @ to mention someone"
      rows="16"
    ></textarea>
    {#if mentionQuery && suggestions.length > 0}
      <ul class="suggest-list">
        {#each suggestions as user (user.id)}
          <li>
            <button type="button" onclick={() => selectMention(user)}>
              {user.displayName}
            </button>
          </li>
        {/each}
      </ul>
    {/if}
  </div>
{/if}
