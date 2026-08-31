<script setup lang="ts">
import { ref, watch } from 'vue';
import { api } from '../api/client';
import type { PublicNoteView } from '../api/types';
import MarkdownView from '../components/MarkdownView.vue';

const props = defineProps<{ token: string }>();

const note = ref<PublicNoteView | null>(null);
const error = ref<string | null>(null);

watch(
  () => props.token,
  (token) => {
    note.value = null;
    error.value = null;
    api
      .getPublicNote(token)
      .then((n) => {
        note.value = n;
      })
      .catch(() => {
        error.value = 'This note is not available. The link may have been revoked.';
      });
  },
  { immediate: true },
);
</script>

<template>
  <div v-if="error" class="page">
    <p class="text-danger">{{ error }}</p>
  </div>
  <div v-else-if="!note" class="page">
    <p>Loading…</p>
  </div>
  <div v-else class="page">
    <p class="public-pill">Shared read-only note</p>
    <h1 class="public-note-title">{{ note.title }}</h1>
    <MarkdownView :markdown="note.contentMarkdown" />
  </div>
</template>
