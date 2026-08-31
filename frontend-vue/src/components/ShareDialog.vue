<script setup lang="ts">
import { ref, watch } from 'vue';
import { api } from '../api/client';
import type { SharePermission, SharesResponse, UserSummary } from '../api/types';

const props = defineProps<{ noteId: string }>();
const emit = defineEmits<{ close: [] }>();

const shares = ref<SharesResponse | null>(null);
const query = ref('');
const results = ref<UserSummary[]>([]);
const selectedUser = ref<UserSummary | null>(null);
const permission = ref<SharePermission>('read');
const error = ref<string | null>(null);

function reload() {
  api
    .listShares(props.noteId)
    .then((res) => {
      shares.value = res;
    })
    .catch((err: unknown) => {
      error.value = err instanceof Error ? err.message : 'Failed to load shares';
    });
}

reload();

let debounceTimer: ReturnType<typeof setTimeout> | undefined;
watch(query, (q) => {
  clearTimeout(debounceTimer);
  if (q.length === 0) {
    results.value = [];
    return;
  }
  debounceTimer = setTimeout(() => {
    api
      .searchUsers(q)
      .then((res) => {
        results.value = res;
      })
      .catch(() => {
        results.value = [];
      });
  }, 200);
});

function handleQueryInput(e: Event) {
  selectedUser.value = null;
  query.value = (e.target as HTMLInputElement).value;
}

async function handleShare() {
  if (!selectedUser.value) return;
  error.value = null;
  try {
    await api.shareWithUser(props.noteId, selectedUser.value.id, permission.value);
    selectedUser.value = null;
    query.value = '';
    reload();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to share note';
  }
}

async function handleRevoke(userId: string) {
  await api.revokeShare(props.noteId, userId);
  reload();
}

async function handleCreatePublicLink() {
  await api.createPublicShare(props.noteId);
  reload();
}

async function handleRevokePublicLink() {
  await api.revokePublicShare(props.noteId);
  reload();
}
</script>

<template>
  <div class="dialog-overlay" @click="emit('close')">
    <div class="dialog" @click.stop>
      <h2>Share note</h2>
      <p v-if="error" class="text-danger">{{ error }}</p>

      <section>
        <h3>Share with a person</h3>
        <div class="share-search-row">
          <input
            type="text"
            class="field"
            placeholder="Search by name or email"
            :value="selectedUser ? selectedUser.displayName : query"
            @input="handleQueryInput"
          />
          <ul v-if="results.length > 0 && !selectedUser" class="suggest-list">
            <li v-for="user in results" :key="user.id">
              <button type="button" @click="selectedUser = user">{{ user.displayName }}</button>
            </li>
          </ul>
          <select class="field" v-model="permission">
            <option value="read">Read only</option>
            <option value="edit">Can edit</option>
          </select>
          <button type="button" class="btn" :disabled="!selectedUser" @click="handleShare">Share</button>
        </div>

        <ul class="share-list">
          <li v-for="share in shares?.userShares" :key="share.user.id" class="share-row">
            <span class="share-row-name">{{ share.user.displayName }}</span>
            <span class="share-row-permission">{{ share.permission === 'edit' ? 'Can edit' : 'Read only' }}</span>
            <button type="button" class="btn" @click="handleRevoke(share.user.id)">Remove</button>
          </li>
        </ul>
      </section>

      <section>
        <h3>Public link</h3>
        <div v-if="shares?.publicShare" class="public-link-row">
          <input
            type="text"
            class="field"
            readonly
            :value="shares.publicShare.url"
            @focus="($event.target as HTMLInputElement).select()"
          />
          <button type="button" class="btn" @click="handleRevokePublicLink">Revoke</button>
        </div>
        <button v-else type="button" class="btn" @click="handleCreatePublicLink">
          Create public link (read-only)
        </button>
      </section>

      <button type="button" class="btn" @click="emit('close')">Done</button>
    </div>
  </div>
</template>
