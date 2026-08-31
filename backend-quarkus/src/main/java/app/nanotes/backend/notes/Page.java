package app.nanotes.backend.notes;

import java.util.List;

/** {@code nextCursor} is null when there are no more pages. */
public record Page(List<NoteSummary> items, String nextCursor) {}
