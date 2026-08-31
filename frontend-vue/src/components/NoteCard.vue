<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api/client';
import type { NoteSummary } from '../api/types';
import { extractMentionedUserIds } from '../utils/mentions';
import { setTaskItemChecked } from '../utils/taskList';
import MarkdownView from './MarkdownView.vue';

const PERMISSION_LABEL: Record<NoteSummary['myPermission'], string> = {
  owner: 'Owner',
  edit: 'Can edit',
  read: 'Read only',
};

const props = defineProps<{ note: NoteSummary }>();
const router = useRouter();
const href = `/notes/${props.note.id}`;
const content = ref(props.note.contentMarkdown);
const canEdit = props.note.myPermission !== 'read';

const clampRef = ref<HTMLDivElement | null>(null);
const isClamped = ref(false);
let observer: ResizeObserver | null = null;

// The fade shadow should only show when content is actually cut off by
// the card's max-height. Re-measured whenever the rendered content
// changes size — including asynchronously, e.g. an image inside the
// note finishing loading after the initial render.
function checkOverflow() {
  const clamp = clampRef.value;
  if (!clamp) return;
  isClamped.value = clamp.scrollHeight > clamp.clientHeight + 1;
}

watch(
  content,
  () => {
    nextTick(() => {
      observer?.disconnect();
      const clamp = clampRef.value;
      const rendered = clamp?.firstElementChild;
      if (!clamp || !rendered) return;
      checkOverflow();
      observer = new ResizeObserver(checkOverflow);
      observer.observe(rendered);
    });
  },
  { immediate: true },
);

onBeforeUnmount(() => observer?.disconnect());

// The card renders the full note, which can itself contain <a> tags, so
// the card can't be an <a>. The title link is the accessible navigation
// path; this handler is a mouse-only convenience that steps aside when
// the click lands on a link or on a text selection.
function handleClick(e: MouseEvent) {
  if (e.defaultPrevented) return;
  if ((e.target as HTMLElement).closest('a')) return;
  if (window.getSelection()?.toString()) return;
  router.push(href);
}

// NoteSummary carries neither the note version nor its mentions, so a
// checkbox toggle re-fetches the note, applies the same toggle to the
// authoritative body, and saves that. The card updates optimistically
// and rolls back if the write fails.
async function handleToggleTask(index: number, checked: boolean) {
  const previous = content.value;
  content.value = setTaskItemChecked(previous, index, checked);
  try {
    const full = await api.getNote(props.note.id);
    const nextMarkdown = setTaskItemChecked(full.contentMarkdown, index, checked);
    const saved = await api.updateNote(props.note.id, full.version, {
      title: full.title,
      contentMarkdown: nextMarkdown,
      mentionedUserIds: extractMentionedUserIds(nextMarkdown),
    });
    content.value = saved.contentMarkdown;
  } catch {
    content.value = previous;
  }
}
</script>

<template>
  <div class="note-card" @click="handleClick">
    <h3 class="note-card-title">
      <RouterLink :to="href">{{ note.title }}</RouterLink>
    </h3>
    <div ref="clampRef" class="note-card-clip">
      <MarkdownView
        :markdown="content"
        class="markdown-view--card"
        :interactive="canEdit"
        @toggle-task="handleToggleTask"
      />
      <div v-if="isClamped" class="note-card-fade" />
    </div>
    <div class="note-card-meta">
      <span>{{ PERMISSION_LABEL[note.myPermission] }}</span>
      <span v-if="note.isPublic">Public</span>
      <time :datetime="note.updatedAt">{{ new Date(note.updatedAt).toLocaleDateString() }}</time>
    </div>
  </div>
</template>
