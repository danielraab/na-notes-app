import { writable } from 'svelte/store';
import type { Action } from 'svelte/action';

// A minimal client-side router: no external routing library, just the
// History API plus a Svelte store for the current pathname. The app only
// ever has four routes (see App.svelte), so a small hand-rolled router is
// less to maintain than pulling in a dependency for it — see
// docs/decisions/0005-custom-router.md.

export const currentPath = writable(window.location.pathname);

function syncFromLocation(): void {
  currentPath.set(window.location.pathname);
}

window.addEventListener('popstate', syncFromLocation);

export function navigate(to: string, options: { replace?: boolean } = {}): void {
  if (options.replace) {
    window.history.replaceState(null, '', to);
  } else {
    window.history.pushState(null, '', to);
  }
  syncFromLocation();
}

export interface RouteMatch {
  params: Record<string, string>;
}

/** Matches a `/notes/:id`-style pattern against a pathname. */
export function matchRoute(pattern: string, path: string): RouteMatch | null {
  const patternParts = pattern.split('/').filter(Boolean);
  const pathParts = path.split('/').filter(Boolean);
  if (patternParts.length !== pathParts.length) return null;

  const params: Record<string, string> = {};
  for (let i = 0; i < patternParts.length; i++) {
    const part = patternParts[i];
    if (part.startsWith(':')) {
      params[part.slice(1)] = decodeURIComponent(pathParts[i]);
    } else if (part !== pathParts[i]) {
      return null;
    }
  }
  return { params };
}

// Svelte action for in-app <a href="..."> navigation: intercepts a plain
// left-click (no modifier keys, no target="_blank") and routes it through
// the History API instead of a full page load.
export const link: Action<HTMLAnchorElement> = (node) => {
  function handleClick(event: MouseEvent) {
    if (event.defaultPrevented || event.button !== 0) return;
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    if (node.target === '_blank') return;
    const href = node.getAttribute('href');
    if (!href || /^([a-z]+:)?\/\//i.test(href)) return;
    event.preventDefault();
    navigate(href);
  }

  node.addEventListener('click', handleClick);
  return {
    destroy() {
      node.removeEventListener('click', handleClick);
    },
  };
};
