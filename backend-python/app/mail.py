"""Sends the share/mention notification emails required by the product
spec, over plain SMTP (stdlib smtplib — no extra dependency needed).
"""

from __future__ import annotations

import logging
import smtplib
from email.message import EmailMessage

logger = logging.getLogger("app.mail")


class Mailer:
    def __init__(self, host: str, port: int, username: str, password: str, from_addr: str) -> None:
        self._host = host
        self._port = port
        self._username = username
        self._password = password
        self._from = from_addr

    def _send(self, to: str, subject: str, body: str) -> None:
        msg = EmailMessage()
        msg["From"] = self._from
        msg["To"] = to
        msg["Subject"] = subject
        msg.set_content(body)

        with smtplib.SMTP(self._host, self._port, timeout=10) as smtp:
            if self._username:
                smtp.login(self._username, self._password)
            smtp.send_message(msg)

    def _send_best_effort(self, to: str, subject: str, body: str) -> None:
        """A notification email failing to send must never fail the API
        request that triggered it (e.g. saving a note the user just wrote).
        """
        try:
            self._send(to, subject, body)
        except OSError:
            logger.exception("failed to send notification email to %s", to)

    def notify_note_shared(
        self, to: str, actor_name: str, note_title: str, note_url: str, editable: bool
    ) -> None:
        access = "editable" if editable else "read-only"
        subject = f"{actor_name} shared a note with you: {note_title}"
        body = (
            f'{actor_name} shared the note "{note_title}" with you ({access} access).\n\n'
            f"Open it here: {note_url}\n"
        )
        self._send_best_effort(to, subject, body)

    def notify_mentioned(self, to: str, actor_name: str, note_title: str, note_url: str) -> None:
        subject = f"{actor_name} mentioned you in a note: {note_title}"
        body = f'{actor_name} mentioned you in the note "{note_title}".\n\nOpen it here: {note_url}\n'
        self._send_best_effort(to, subject, body)
