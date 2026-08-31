import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/dashboard-page/dashboard-page').then((m) => m.DashboardPage),
  },
  {
    path: 'notes/new',
    loadComponent: () => import('./pages/note-editor-page/note-editor-page').then((m) => m.NoteEditorPage),
  },
  {
    path: 'notes/:id',
    loadComponent: () => import('./pages/note-editor-page/note-editor-page').then((m) => m.NoteEditorPage),
  },
  {
    path: 'shared/:token',
    loadComponent: () => import('./pages/public-note-page/public-note-page').then((m) => m.PublicNotePage),
  },
];
