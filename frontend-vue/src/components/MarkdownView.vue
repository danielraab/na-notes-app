<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { normalizeTaskLines } from '../utils/taskList';

// Notes can be viewed by people other than their author (shares, public
// links), so rendered markdown is always sanitized before being injected
// as HTML — never trust it just because it came from our own API.
const props = defineProps<{
  markdown: string;
  class?: string;
  // When provided, GFM task-list checkboxes render enabled and emitting
  // 'toggle-task' with the checkbox's document-order index (0-based) and
  // its new state lets the caller persist the change. Omitted -> checkboxes
  // stay read-only, as `marked` renders them.
  interactive?: boolean;
}>();

const emit = defineEmits<{
  'toggle-task': [index: number, checked: boolean];
}>();

const containerRef = ref<HTMLDivElement | null>(null);

const html = computed(() => {
  const rawHtml = marked.parse(normalizeTaskLines(props.markdown), { async: false, breaks: true });
  return DOMPurify.sanitize(rawHtml);
});

// `marked` renders task-list checkboxes with `disabled`. Re-enable them
// (rather than rewriting the sanitized HTML) whenever the caller wants
// them interactive; re-runs after each content change.
watch(
  html,
  () => {
    if (!props.interactive) return;
    nextTick(() => {
      for (const box of containerRef.value?.querySelectorAll<HTMLInputElement>('input[type="checkbox"]') ?? []) {
        box.disabled = false;
      }
    });
  },
  { immediate: true },
);

function handleClick(e: MouseEvent) {
  if (!props.interactive) return;
  const target = e.target as HTMLElement;
  if (!(target instanceof HTMLInputElement) || target.type !== 'checkbox') return;
  // Don't let the toggle bubble to an enclosing click handler (e.g. the
  // note card's navigate-on-click).
  e.stopPropagation();
  const boxes = containerRef.value?.querySelectorAll('input[type="checkbox"]');
  if (!boxes) return;
  const index = Array.prototype.indexOf.call(boxes, target);
  if (index === -1) return;
  emit('toggle-task', index, target.checked);
}
</script>

<template>
  <div
    ref="containerRef"
    :class="props.class ? `markdown-view ${props.class}` : 'markdown-view'"
    @click="handleClick"
    v-html="html"
  />
</template>
