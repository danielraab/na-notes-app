<script setup lang="ts">
import { ref, watch } from 'vue';
import { api } from '../api/client';
import type { UserSummary } from '../api/types';
import { findMentionQuery, insertMention } from '../utils/mentions';
import type { MentionQuery } from '../utils/mentions';
import MarkdownView from './MarkdownView.vue';

// Always-Markdown source of truth (per product spec, rich text is
// optional and out of scope here): a plain textarea with a live preview
// tab, plus "@" mention autocomplete backed by /api/users/search.
const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const showPreview = ref(false);
const mentionQuery = ref<MentionQuery | null>(null);
const suggestions = ref<UserSummary[]>([]);
const textareaRef = ref<HTMLTextAreaElement | null>(null);

let debounceTimer: ReturnType<typeof setTimeout> | undefined;
watch(mentionQuery, (query) => {
  clearTimeout(debounceTimer);
  if (!query) {
    suggestions.value = [];
    return;
  }
  debounceTimer = setTimeout(() => {
    api
      .searchUsers(query.query)
      .then((results) => {
        suggestions.value = results;
      })
      .catch(() => {
        suggestions.value = [];
      });
  }, 200);
});

function handleInput(e: Event) {
  const target = e.target as HTMLTextAreaElement;
  emit('update:modelValue', target.value);
  mentionQuery.value = findMentionQuery(target.value, target.selectionStart);
}

function syncCursor() {
  const el = textareaRef.value;
  if (!el) return;
  mentionQuery.value = findMentionQuery(props.modelValue, el.selectionStart);
}

function handleKeyDown(e: KeyboardEvent) {
  if (mentionQuery.value && suggestions.value.length > 0 && e.key === 'Escape') {
    mentionQuery.value = null;
  }
}

function selectMention(user: UserSummary) {
  if (!mentionQuery.value) return;
  emit('update:modelValue', insertMention(props.modelValue, mentionQuery.value, user.displayName, user.id));
  mentionQuery.value = null;
  textareaRef.value?.focus();
}
</script>

<template>
  <div>
    <div class="editor-tabs">
      <button type="button" :class="!showPreview ? 'btn btn-primary' : 'btn'" @click="showPreview = false">
        Write
      </button>
      <button type="button" :class="showPreview ? 'btn btn-primary' : 'btn'" @click="showPreview = true">
        Preview
      </button>
    </div>

    <MarkdownView v-if="showPreview" :markdown="modelValue" />
    <div v-else class="editor-textarea-wrap">
      <textarea
        ref="textareaRef"
        class="editor-textarea"
        :value="modelValue"
        placeholder="Write your note in Markdown... type @ to mention someone"
        rows="16"
        @input="handleInput"
        @keyup="syncCursor"
        @keydown="handleKeyDown"
        @click="syncCursor"
      />
      <ul v-if="mentionQuery && suggestions.length > 0" class="suggest-list">
        <li v-for="user in suggestions" :key="user.id">
          <button type="button" @click="selectMention(user)">{{ user.displayName }}</button>
        </li>
      </ul>
    </div>
  </div>
</template>
