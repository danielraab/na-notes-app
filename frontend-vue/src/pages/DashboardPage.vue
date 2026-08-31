<script setup lang="ts">
import { useAuth } from '../auth/auth';
import NoteGrid from '../components/NoteGrid.vue';
import NotePreview from '../components/NotePreview.vue';

const { state: auth, login } = useAuth();
</script>

<template>
  <p v-if="auth.loading" class="page">Loading…</p>
  <div v-else class="page">
    <!-- Keyed by user id so the feed composable re-runs its initial load
         when the signed-in user changes, instead of reusing stale state. -->
    <NoteGrid v-if="auth.user" :key="auth.user.id" />
    <template v-else>
      <div class="banner">
        <p>Sign in to create your own notes, share them, and mention teammates.</p>
        <button type="button" class="btn" @click="login('/')">Log in</button>
      </div>
      <div class="preview-row">
        <NotePreview />
      </div>
    </template>
  </div>
</template>
