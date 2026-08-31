import { createRouter, createWebHistory } from 'vue-router';

// Four flat client-side routes (the backend never renders HTML) — a
// vanilla vue-router setup is the idiomatic choice for a Vue SPA of this
// size; see docs/decisions/0005-vue-router.md.
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../pages/DashboardPage.vue'),
    },
    {
      path: '/notes/new',
      name: 'note-new',
      component: () => import('../pages/NoteEditorPage.vue'),
      props: { isNew: true },
    },
    {
      path: '/notes/:id',
      name: 'note-edit',
      component: () => import('../pages/NoteEditorPage.vue'),
      props: true,
    },
    {
      path: '/shared/:token',
      name: 'public-note',
      component: () => import('../pages/PublicNotePage.vue'),
      props: true,
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../pages/NotFoundPage.vue'),
    },
  ],
});

export default router;
