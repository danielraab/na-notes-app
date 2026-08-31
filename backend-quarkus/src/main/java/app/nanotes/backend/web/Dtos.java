package app.nanotes.backend.web;

import app.nanotes.backend.notes.Note;
import app.nanotes.backend.notes.NoteSummary;
import app.nanotes.backend.notes.Page;
import app.nanotes.backend.notes.PublicNoteView;
import app.nanotes.backend.notes.PublicShare;
import app.nanotes.backend.notes.UserShare;
import app.nanotes.backend.users.User;
import app.nanotes.backend.users.UserSummary;
import app.nanotes.backend.web.dto.NoteDto;
import app.nanotes.backend.web.dto.NotePageDto;
import app.nanotes.backend.web.dto.NoteSummaryDto;
import app.nanotes.backend.web.dto.PublicNoteViewDto;
import app.nanotes.backend.web.dto.PublicShareDto;
import app.nanotes.backend.web.dto.UserDto;
import app.nanotes.backend.web.dto.UserShareDto;
import app.nanotes.backend.web.dto.UserSummaryDto;
import java.util.List;

/**
 * DTOs mirror the schemas in /openapi/openapi.yaml exactly (field names,
 * casing, nullability) — that file is the source of truth; if these
 * diverge from it, the spec is wrong or this code is.
 */
public final class Dtos {

    private Dtos() {}

    public static UserDto toUserDto(User u) {
        return new UserDto(u.id(), u.email(), u.displayName(), u.avatarUrl());
    }

    public static UserSummaryDto toUserSummaryDto(UserSummary u) {
        return new UserSummaryDto(u.id(), u.displayName(), u.avatarUrl());
    }

    public static NoteDto toNoteDto(Note n) {
        return new NoteDto(
                n.id(),
                n.title(),
                n.contentMarkdown(),
                n.ownerId(),
                n.version(),
                n.myPermission().wireValue(),
                n.isPublic(),
                n.createdAt(),
                n.updatedAt());
    }

    public static NoteSummaryDto toNoteSummaryDto(NoteSummary s) {
        return new NoteSummaryDto(
                s.id(), s.title(), s.contentMarkdown(), s.ownerId(), s.myPermission().wireValue(), s.isPublic(), s.updatedAt());
    }

    public static NotePageDto toNotePageDto(Page p) {
        List<NoteSummaryDto> items = p.items().stream().map(Dtos::toNoteSummaryDto).toList();
        return new NotePageDto(items, p.nextCursor());
    }

    public static UserShareDto toUserShareDto(UserShare s) {
        UserSummaryDto user = new UserSummaryDto(s.userId(), s.displayName(), s.avatarUrl());
        return new UserShareDto(user, s.permission().wireValue(), s.createdAt());
    }

    public static PublicShareDto toPublicShareDto(PublicShare ps, String url) {
        return new PublicShareDto(ps.token(), url, ps.createdAt());
    }

    public static PublicNoteViewDto toPublicNoteViewDto(PublicNoteView v) {
        return new PublicNoteViewDto(v.title(), v.contentMarkdown(), v.updatedAt());
    }
}
