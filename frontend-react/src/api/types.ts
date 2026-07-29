// Mirrors the schemas in /openapi/openapi.yaml exactly. That file is the
// source of truth — if these diverge from it, the spec is wrong or this is.

export type Permission = 'owner' | 'edit' | 'read';
export type SharePermission = 'read' | 'edit';

export interface User {
  id: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
}

export interface UserSummary {
  id: string;
  displayName: string;
  avatarUrl: string | null;
}

export interface NoteInput {
  title: string;
  contentMarkdown: string;
  mentionedUserIds: string[];
}

export interface Note {
  id: string;
  title: string;
  contentMarkdown: string;
  ownerId: string;
  version: number;
  myPermission: Permission;
  isPublic: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface NoteSummary {
  id: string;
  title: string;
  excerpt: string;
  ownerId: string;
  myPermission: Permission;
  isPublic: boolean;
  updatedAt: string;
}

export interface NotePage {
  items: NoteSummary[];
  nextCursor: string | null;
}

export interface UserShare {
  user: UserSummary;
  permission: SharePermission;
  createdAt: string;
}

export interface PublicShare {
  token: string;
  url: string;
  createdAt: string;
}

export interface SharesResponse {
  userShares: UserShare[];
  publicShare: PublicShare | null;
}

export interface PublicNoteView {
  title: string;
  contentMarkdown: string;
  updatedAt: string;
}
