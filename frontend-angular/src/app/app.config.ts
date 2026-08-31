import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding: route params (`:id`, `:token`) are bound
    // straight to matching component `input()`s — see NoteEditorPage.id
    // and PublicNotePage.token — instead of reading ActivatedRoute by hand.
    provideRouter(routes, withComponentInputBinding()),
  ],
};
