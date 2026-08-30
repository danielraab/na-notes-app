//! Sends the share/mention notification emails required by the product
//! spec, over plain SMTP.

use lettre::message::header::ContentType;
use lettre::transport::smtp::authentication::Credentials;
use lettre::{Message, SmtpTransport, Transport};

#[derive(Clone)]
pub struct Mailer {
    host: String,
    port: u16,
    username: String,
    password: String,
    from: String,
}

impl Mailer {
    pub fn new(
        host: String,
        port: u16,
        username: String,
        password: String,
        from: String,
    ) -> Mailer {
        Mailer {
            host,
            port,
            username,
            password,
            from,
        }
    }

    fn send(&self, to: &str, subject: &str, body: &str) -> Result<(), String> {
        let message = Message::builder()
            .from(self.from.parse().map_err(|e| format!("parse From: {e}"))?)
            .to(to.parse().map_err(|e| format!("parse To: {e}"))?)
            .subject(subject)
            .header(ContentType::TEXT_PLAIN)
            .body(body.to_string())
            .map_err(|e| format!("build message: {e}"))?;

        let mut builder = SmtpTransport::builder_dangerous(&self.host).port(self.port);
        if !self.username.is_empty() {
            builder = builder.credentials(Credentials::new(
                self.username.clone(),
                self.password.clone(),
            ));
        }
        let mailer = builder.build();

        mailer
            .send(&message)
            .map(|_| ())
            .map_err(|e| format!("send mail to {to}: {e}"))
    }

    /// Logs failures instead of returning them: a notification email
    /// failing to send must never fail the API request that triggered it
    /// (e.g. saving a note the user just wrote).
    fn send_best_effort(&self, to: String, subject: String, body: String) {
        if let Err(err) = self.send(&to, &subject, &body) {
            tracing::error!(to, error = %err, "failed to send notification email");
        }
    }

    pub async fn notify_note_shared(
        &self,
        to: String,
        actor_name: String,
        note_title: String,
        note_url: String,
        editable: bool,
    ) {
        let access = if editable { "editable" } else { "read-only" };
        let subject = format!("{actor_name} shared a note with you: {note_title}");
        let body = format!(
            "{actor_name} shared the note \"{note_title}\" with you ({access} access).\n\nOpen it here: {note_url}\n"
        );
        let this = self.clone();
        let _ = tokio::task::spawn_blocking(move || this.send_best_effort(to, subject, body)).await;
    }

    pub async fn notify_mentioned(
        &self,
        to: String,
        actor_name: String,
        note_title: String,
        note_url: String,
    ) {
        let subject = format!("{actor_name} mentioned you in a note: {note_title}");
        let body = format!("{actor_name} mentioned you in the note \"{note_title}\".\n\nOpen it here: {note_url}\n");
        let this = self.clone();
        let _ = tokio::task::spawn_blocking(move || this.send_best_effort(to, subject, body)).await;
    }
}
