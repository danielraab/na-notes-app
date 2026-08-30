<script lang="ts">
  import { auth } from '../auth/auth.svelte';
  import { VersionConflictError, api } from '../api/client';
  import type { Note } from '../api/types';
  import { navigate } from '../lib/router';
  import MarkdownEditor from '../components/MarkdownEditor.svelte';
  import MarkdownView from '../components/MarkdownView.svelte';
  import ShareDialog from '../components/ShareDialog.svelte';
  import { extractMentionedUserIds } from '../utils/mentions';

  let { id }: { id?: string } = $props();
  const isNew = $derived(id === undefined);

  let note = $state<Note | null>(null);
  let title = $state('');
  let content = $state('');
  // Intentionally captures only the initial value: App.svelte keys this
  // component by the route id (`{#key id}`), so a changed id remounts it
  // instead of updating the prop in place.
  // svelte-ignore state_referenced_locally
  let loading = $state(id !== undefined);
  let saving = $state(false);
  let justSaved = $state(false);
  let error = $state<string | null>(null);
  let conflict = $state<Note | null>(null);
  let showShare = $state(false);

  $effect(() => {
    if (isNew || !id) return;
    loading = true;
    api
      .getNote(id)
      .then((n) => {
        note = n;
        title = n.title;
        content = n.contentMarkdown;
      })
      .catch((err: unknown) => {
        error = err instanceof Error ? err.message : 'Failed to load note';
      })
      .finally(() => {
        loading = false;
      });
  });

  $effect(() => {
    if (!justSaved) return;
    const timer = setTimeout(() => (justSaved = false), 3000);
    return () => clearTimeout(timer);
  });

  const readOnly = $derived(note !== null && note.myPermission === 'read');
  const canManageSharing = $derived(note?.myPermission === 'owner');
  const canDelete = $derived(note?.myPermission === 'owner');

  async function handleSave() {
    saving = true;
    error = null;
    conflict = null;
    justSaved = false;
    const mentionedUserIds = extractMentionedUserIds(content);
    try {
      if (isNew) {
        const created = await api.createNote({ title, contentMarkdown: content, mentionedUserIds });
        justSaved = true;
        navigate(`/notes/${created.id}`, { replace: true });
      } else if (note) {
        const updated = await api.updateNote(note.id, note.version, {
          title,
          contentMarkdown: content,
          mentionedUserIds,
        });
        note = updated;
        justSaved = true;
      }
    } catch (err) {
      if (err instanceof VersionConflictError) {
        conflict = err.currentNote;
      } else {
        error = err instanceof Error ? err.message : 'Failed to save note';
      }
    } finally {
      saving = false;
    }
  }

  async function handleDelete() {
    if (!note || !window.confirm('Delete this note? This cannot be undone.')) return;
    await api.deleteNote(note.id);
    navigate('/');
  }

  function reloadLatest() {
    if (!conflict) return;
    note = conflict;
    title = conflict.title;
    content = conflict.contentMarkdown;
    conflict = null;
  }
</script>

{#if !auth.loading && !auth.user}
  <div class="page">
    <p class="signin-prompt">You need to be signed in to {isNew ? 'create' : 'view'} this note.</p>
    <button type="button" class="btn" onclick={() => auth.login(window.location.pathname)}>Log in</button>
  </div>
{:else if loading}
  <p class="page">Loading…</p>
{:else if error}
  <p class="page text-danger">{error}</p>
{:else}
  <div class="page">
    {#if conflict}
      <div class="banner">
        <p>This note was changed by someone else since you opened it. Your unsaved edits are still below.</p>
        <button type="button" class="btn" onclick={reloadLatest}>
          Load the latest version (discards your changes)
        </button>
      </div>
    {/if}

    <input
      class="editor-title"
      value={title}
      oninput={(e) => {
        title = (e.currentTarget as HTMLInputElement).value;
        justSaved = false;
      }}
      placeholder="Untitled note"
      disabled={readOnly}
    />

    {#if readOnly}
      <MarkdownView markdown={content} />
    {:else}
      <MarkdownEditor
        value={content}
        onChange={(v) => {
          content = v;
          justSaved = false;
        }}
      />
    {/if}

    <div class="editor-actions">
      {#if !readOnly}
        <button type="button" class="btn btn-primary" onclick={handleSave} disabled={saving || title.trim() === ''}>
          {saving ? 'Saving…' : 'Save'}
        </button>
      {/if}
      {#if canManageSharing}
        <button type="button" class="btn" onclick={() => (showShare = true)}>Share</button>
      {/if}
      {#if canDelete}
        <button type="button" class="btn btn-danger" onclick={handleDelete}>Delete</button>
      {/if}
      {#if justSaved}
        <span role="status" class="save-status"><span aria-hidden="true">✓</span> Saved</span>
      {/if}
    </div>

    {#if showShare && note}
      <ShareDialog noteId={note.id} onClose={() => (showShare = false)} />
    {/if}
  </div>
{/if}
