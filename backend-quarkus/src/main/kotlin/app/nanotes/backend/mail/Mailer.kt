package app.nanotes.backend.mail

import io.quarkus.mailer.Mail
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

/**
 * Sends the share/mention notification emails required by the product
 * spec, via Quarkus's `quarkus-mailer` extension (configured from
 * `SMTP_*` env vars in application.properties).
 */
@ApplicationScoped
class Mailer(private val delegate: io.quarkus.mailer.Mailer) {

    companion object {
        private val LOG: Logger = Logger.getLogger(Mailer::class.java)
    }

    /**
     * Logs failures instead of throwing: a notification email failing to
     * send must never fail the API request that triggered it (e.g. saving a
     * note the user just wrote).
     */
    private fun sendBestEffort(to: String, subject: String, body: String) {
        try {
            delegate.send(Mail.withText(to, subject, body))
        } catch (e: RuntimeException) {
            LOG.errorf(e, "failed to send notification email to %s", to)
        }
    }

    fun notifyNoteShared(to: String, actorName: String, noteTitle: String, noteUrl: String, editable: Boolean) {
        val access = if (editable) "editable" else "read-only"
        val subject = "$actorName shared a note with you: $noteTitle"
        val body = "$actorName shared the note \"$noteTitle\" with you ($access access).\n\nOpen it here: $noteUrl\n"
        sendBestEffort(to, subject, body)
    }

    fun notifyMentioned(to: String, actorName: String, noteTitle: String, noteUrl: String) {
        val subject = "$actorName mentioned you in a note: $noteTitle"
        val body = "$actorName mentioned you in the note \"$noteTitle\".\n\nOpen it here: $noteUrl\n"
        sendBestEffort(to, subject, body)
    }
}
