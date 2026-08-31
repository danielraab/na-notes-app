-- Mirrors /docs/schema.md (ADR 0014), which was itself extracted from
-- backend-go's own copy of this schema. Update /docs/schema.md if a future
-- migration here changes the data model, not just SQL-dialect-level detail.

CREATE TABLE users (
    id TEXT PRIMARY KEY,
    oidc_subject TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE notes (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content_markdown TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX idx_notes_owner_updated ON notes (owner_id, updated_at DESC, id DESC);

CREATE TABLE note_shares (
    note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission TEXT NOT NULL CHECK (permission IN ('read', 'edit')),
    created_at TEXT NOT NULL,
    PRIMARY KEY (note_id, user_id)
);
CREATE INDEX idx_note_shares_user_id ON note_shares (user_id);

CREATE TABLE note_public_shares (
    note_id TEXT PRIMARY KEY REFERENCES notes(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL
);

CREATE TABLE note_mentions (
    note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    PRIMARY KEY (note_id, user_id)
);

CREATE TABLE sessions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    csrf_token TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_sessions_user_id ON sessions (user_id);

-- Short-lived storage for in-flight OIDC authorization-code+PKCE exchanges.
CREATE TABLE oidc_requests (
    state TEXT PRIMARY KEY,
    code_verifier TEXT NOT NULL,
    redirect_to TEXT NOT NULL,
    expires_at TEXT NOT NULL
);
