package app.nanotes.backend.mail;

import io.quarkus.mailer.Mail;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Sends the share/mention notification emails required by the product
 * spec, via Quarkus's {@code quarkus-mailer} extension (configured from
 * {@code SMTP_*} env vars in application.properties).
 */
@ApplicationScoped
public class Mailer {

    private static final Logger LOG = Logger.getLogger(Mailer.class);

    private final io.quarkus.mailer.Mailer mailer;

    public Mailer(io.quarkus.mailer.Mailer mailer) {
        this.mailer = mailer;
    }

    /**
     * Logs failures instead of throwing: a notification email failing to
     * send must never fail the API request that triggered it (e.g. saving a
     * note the user just wrote).
     */
    private void sendBestEffort(String to, String subject, String body) {
        try {
            mailer.send(Mail.withText(to, subject, body));
        } catch (RuntimeException e) {
            LOG.errorf(e, "failed to send notification email to %s", to);
        }
    }

    public void notifyNoteShared(String to, String actorName, String noteTitle, String noteUrl, boolean editable) {
        String access = editable ? "editable" : "read-only";
        String subject = "%s shared a note with you: %s".formatted(actorName, noteTitle);
        String body = "%s shared the note \"%s\" with you (%s access).\n\nOpen it here: %s\n"
                .formatted(actorName, noteTitle, access, noteUrl);
        sendBestEffort(to, subject, body);
    }

    public void notifyMentioned(String to, String actorName, String noteTitle, String noteUrl) {
        String subject = "%s mentioned you in a note: %s".formatted(actorName, noteTitle);
        String body = "%s mentioned you in the note \"%s\".\n\nOpen it here: %s\n".formatted(actorName, noteTitle, noteUrl);
        sendBestEffort(to, subject, body);
    }
}
