import type {
  Note,
  NoteInput,
  NotePage,
  PublicNoteView,
  PublicShare,
  SharePermission,
  SharesResponse,
  User,
  UserShare,
  UserSummary,
} from './types';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function readCookie(name: string): string | undefined {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : undefined;
}

export class ApiError extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

// Thrown only by updateNote: the backend rejects a stale write with 409
// and returns the current server copy of the note (ADR 0008) instead of
// the generic {error} envelope, so it needs its own error type.
export class VersionConflictError extends Error {
  currentNote: Note;

  constructor(currentNote: Note) {
    super('The note changed since it was last loaded.');
    this.name = 'VersionConflictError';
    this.currentNote = currentNote;
  }
}

function buildHeaders(hasBody: boolean, method: string, extra?: HeadersInit): Headers {
  const headers = new Headers(extra);
  if (hasBody && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (MUTATING_METHODS.has(method)) {
    const csrfToken = readCookie('csrf_token');
    if (csrfToken) headers.set('X-CSRF-Token', csrfToken);
  }
  return headers;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    method,
    credentials: 'include',
    headers: buildHeaders(init.body != null, method, init.headers),
  });

  if (res.status === 204) {
    return undefined as T;
  }

  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!res.ok) {
    const code = data?.error?.code ?? 'UNKNOWN';
    const message = data?.error?.message ?? res.statusText;
    throw new ApiError(res.status, code, message);
  }
  return data as T;
}

export function loginUrl(redirectTo: string): string {
  return `${API_BASE_URL}/auth/login?redirectTo=${encodeURIComponent(redirectTo)}`;
}

export const api = {
  me: () => request<User>('/auth/me'),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),

  searchUsers: (q: string) => request<UserSummary[]>(`/users/search?${new URLSearchParams({ q })}`),

  listNotes: (cursor: string | null, limit = 12) => {
    const params = new URLSearchParams({ limit: String(limit) });
    if (cursor) params.set('cursor', cursor);
    return request<NotePage>(`/notes?${params}`);
  },
  getNote: (id: string) => request<Note>(`/notes/${id}`),
  createNote: (input: NoteInput) =>
    request<Note>('/notes', { method: 'POST', body: JSON.stringify(input) }),
  deleteNote: (id: string) => request<void>(`/notes/${id}`, { method: 'DELETE' }),

  // Not routed through request(): a 409 response body is a bare Note, not
  // the {error} envelope every other endpoint uses (ADR 0008).
  async updateNote(id: string, version: number, input: NoteInput): Promise<Note> {
    const res = await fetch(`${API_BASE_URL}/notes/${id}`, {
      method: 'PUT',
      credentials: 'include',
      headers: buildHeaders(true, 'PUT', { 'If-Match': String(version) }),
      body: JSON.stringify(input),
    });
    const data = await res.json();
    if (res.status === 409) {
      throw new VersionConflictError(data as Note);
    }
    if (!res.ok) {
      throw new ApiError(res.status, data?.error?.code ?? 'UNKNOWN', data?.error?.message ?? res.statusText);
    }
    return data as Note;
  },

  listShares: (noteId: string) => request<SharesResponse>(`/notes/${noteId}/shares`),
  shareWithUser: (noteId: string, userId: string, permission: SharePermission) =>
    request<UserShare>(`/notes/${noteId}/shares`, {
      method: 'POST',
      body: JSON.stringify({ userId, permission }),
    }),
  revokeShare: (noteId: string, userId: string) =>
    request<void>(`/notes/${noteId}/shares/${userId}`, { method: 'DELETE' }),
  createPublicShare: (noteId: string) =>
    request<PublicShare>(`/notes/${noteId}/public-share`, { method: 'POST' }),
  revokePublicShare: (noteId: string) =>
    request<void>(`/notes/${noteId}/public-share`, { method: 'DELETE' }),

  getPublicNote: (token: string) => request<PublicNoteView>(`/public/notes/${token}`),
};
