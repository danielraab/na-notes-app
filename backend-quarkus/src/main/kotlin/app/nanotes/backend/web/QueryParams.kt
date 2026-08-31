package app.nanotes.backend.web

import app.nanotes.backend.apperr.ValidationException

internal object QueryParams {
    fun parsePositiveInt(raw: String, errorMessage: String): Int {
        val v = parseInt(raw, errorMessage)
        if (v <= 0) throw ValidationException(errorMessage)
        return v
    }

    fun parseInt(raw: String?, errorMessage: String): Int =
        raw?.toIntOrNull() ?: throw ValidationException(errorMessage)
}
