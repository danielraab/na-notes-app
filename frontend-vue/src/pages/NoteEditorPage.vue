<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuth } from '../auth/auth';
import { VersionConflictError, api } from '../api/client';
import type { Note } from '../api/types';
import MarkdownEditor from '../components/MarkdownEditor.vue';
import MarkdownView from '../components/MarkdownView.vue';
import ShareDialog from '../components/ShareDialog.vue';
import { extractMentionedUserIds } from '../utils/mentions';

const props = defineProps<{ id?: string; isNew?: boolean }>();
const isNew = computed(() => props.isNew === true || props.id === undefined);

const router = useRouter();
const route = useRoute();
const { state: auth, login } = useAuth();

const note = ref<Note | null>(null);
const title = ref('');
const content = ref('');
const loading = ref(!isNew.value);
const saving = ref(false);
const justSaved = ref(false);
const error = ref<string | null>(null);
const conflict = ref<Note | null>(null);
const showShare = ref(false);

watch(
  () => props.id,
  (id) => {
    if (isNew.value || !id) return;
    loading.value = true;
    api
      .getNote(id)
      .then((n) => {
        note.value = n;
        title.value = n.title;
        content.value = n.contentMarkdown;
      })
      .catch((err: unknown) => {
        error.value = err instanceof Error ? err.message : 'Failed to load note';
      })
      .finally(() => {
        loading.value = false;
      });
  },
  { immediate: true },
);

let savedTimer: ReturnType<typeof setTimeout> | undefined;
watch(justSaved, (saved) => {
  clearTimeout(savedTimer);
  if (!saved) return;
  savedTimer = setTimeout(() => {
    justSaved.value = false;
  }, 3000);
});

const readOnly = computed(() => note.value !== null && note.value.myPermission === 'read');
const canManageSharing = computed(() => note.value?.myPermission === 'owner');
const canDelete = computed(() => note.value?.myPermission === 'owner');

function handleTitleInput(e: Event) {
  title.value = (e.target as HTMLInputElement).value;
  justSaved.value = false;
}

function handleContentChange(value: string) {
  content.value = value;
  justSaved.value = false;
}

async function handleSave() {
  saving.value = true;
  error.value = null;
  conflict.value = null;
  justSaved.value = false;
  const mentionedUserIds = extractMentionedUserIds(content.value);
  try {
    if (isNew.value) {
      const created = await api.createNote({ title: title.value, contentMarkdown: content.value, mentionedUserIds });
      justSaved.value = true;
      router.replace(`/notes/${created.id}`);
    } else if (note.value) {
      const updated = await api.updateNote(note.value.id, note.value.version, {
        title: title.value,
        contentMarkdown: content.value,
        mentionedUserIds,
      });
      note.value = updated;
      justSaved.value = true;
    }
  } catch (err) {
    if (err instanceof VersionConflictError) {
      conflict.value = err.currentNote;
    } else {
      error.value = err instanceof Error ? err.message : 'Failed to save note';
    }
  } finally {
    saving.value = false;
  }
}

async function handleDelete() {
  if (!note.value || !window.confirm('Delete this note? This cannot be undone.')) return;
  await api.deleteNote(note.value.id);
  router.push('/');
}

function reloadLatest() {
  if (!conflict.value) return;
  note.value = conflict.value;
  title.value = conflict.value.title;
  content.value = conflict.value.contentMarkdown;
  conflict.value = null;
}
</script>

<template>
  <div v-if="!auth.loading && !auth.user" class="page">
    <p class="signin-prompt">You need to be signed in to {{ isNew ? 'create' : 'view' }} this note.</p>
    <button type="button" class="btn" @click="login(route.fullPath)">Log in</button>
  </div>
  <p v-else-if="loading" class="page">Loading…</p>
  <p v-else-if="error" class="page text-danger">{{ error }}</p>
  <div v-else class="page">
    <div v-if="conflict" class="banner">
      <p>This note was changed by someone else since you opened it. Your unsaved edits are still below.</p>
      <button type="button" class="btn" @click="reloadLatest">
        Load the latest version (discards your changes)
      </button>
    </div>

    <input
      class="editor-title"
      :value="title"
      placeholder="Untitled note"
      :disabled="readOnly"
      @input="handleTitleInput"
    />

    <MarkdownView v-if="readOnly" :markdown="content" />
    <MarkdownEditor v-else :model-value="content" @update:model-value="handleContentChange" />

    <div class="editor-actions">
      <button
        v-if="!readOnly"
        type="button"
        class="btn btn-primary"
        :disabled="saving || title.trim() === ''"
        @click="handleSave"
      >
        {{ saving ? 'Saving…' : 'Save' }}
      </button>
      <button v-if="canManageSharing" type="button" class="btn" @click="showShare = true">Share</button>
      <button v-if="canDelete" type="button" class="btn btn-danger" @click="handleDelete">Delete</button>
      <span v-if="justSaved" role="status" class="save-status">
        <span aria-hidden="true">✓</span> Saved
      </span>
    </div>

    <ShareDialog v-if="showShare && note" :note-id="note.id" @close="showShare = false" />
  </div>
</template>
