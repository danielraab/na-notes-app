package app.nanotes.backend.apperr;

import app.nanotes.backend.notes.Note;

/**
 * The note's stored version no longer matches the client's If-Match header
 * (ADR 0008). Carries the current server copy so the 409 response body can
 * include it, mirroring backend-go's Update returning (Note, error) together.
 */
public class VersionConflictException extends RuntimeException {
    private final Note currentNote;

    public VersionConflictException(Note currentNote) {
        super("note was modified since you last loaded it");
        this.currentNote = currentNote;
    }

    public Note currentNote() {
        return currentNote;
    }
}
