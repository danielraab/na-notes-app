package app.nanotes.backend.web;

import app.nanotes.backend.apperr.ValidationException;

final class QueryParams {
    private QueryParams() {}

    static int parsePositiveInt(String raw, String errorMessage) {
        int v = parseInt(raw, errorMessage);
        if (v <= 0) {
            throw new ValidationException(errorMessage);
        }
        return v;
    }

    static int parseInt(String raw, String errorMessage) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ValidationException(errorMessage);
        }
    }
}
