// Package mail sends the share/mention notification emails required by
// the product spec, over plain SMTP.
package mail

import (
	"fmt"
	"log/slog"
	"net/smtp"
)

type Mailer struct {
	host, port, username, password, from string
}

func New(host, port, username, password, from string) *Mailer {
	return &Mailer{host: host, port: port, username: username, password: password, from: from}
}

func (m *Mailer) send(to, subject, body string) error {
	addr := fmt.Sprintf("%s:%s", m.host, m.port)
	msg := fmt.Sprintf("From: %s\r\nTo: %s\r\nSubject: %s\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n%s\r\n",
		m.from, to, subject, body)

	var auth smtp.Auth
	if m.username != "" {
		auth = smtp.PlainAuth("", m.username, m.password, m.host)
	}
	if err := smtp.SendMail(addr, auth, m.from, []string{to}, []byte(msg)); err != nil {
		return fmt.Errorf("send mail to %s: %w", to, err)
	}
	return nil
}

// sendBestEffort logs failures instead of returning them: a notification
// email failing to send must never fail the API request that triggered it
// (e.g. saving a note the user just wrote).
func (m *Mailer) sendBestEffort(to, subject, body string) {
	if err := m.send(to, subject, body); err != nil {
		slog.Error("failed to send notification email", "to", to, "error", err)
	}
}

func (m *Mailer) NotifyNoteShared(to, actorName, noteTitle, noteURL string, editable bool) {
	access := "read-only"
	if editable {
		access = "editable"
	}
	subject := fmt.Sprintf("%s shared a note with you: %s", actorName, noteTitle)
	body := fmt.Sprintf("%s shared the note %q with you (%s access).\n\nOpen it here: %s\n", actorName, noteTitle, access, noteURL)
	m.sendBestEffort(to, subject, body)
}

func (m *Mailer) NotifyMentioned(to, actorName, noteTitle, noteURL string) {
	subject := fmt.Sprintf("%s mentioned you in a note: %s", actorName, noteTitle)
	body := fmt.Sprintf("%s mentioned you in the note %q.\n\nOpen it here: %s\n", actorName, noteTitle, noteURL)
	m.sendBestEffort(to, subject, body)
}
