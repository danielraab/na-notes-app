import { Component, inject } from '@angular/core';
import { Auth } from '../../auth/auth';
import { NoteGrid } from '../../components/note-grid/note-grid';
import { NotePreview } from '../../components/note-preview/note-preview';

@Component({
  selector: 'app-dashboard-page',
  imports: [NoteGrid, NotePreview],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css',
})
export class DashboardPage {
  protected readonly auth = inject(Auth);
}
