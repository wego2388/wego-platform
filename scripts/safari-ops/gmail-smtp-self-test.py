#!/usr/bin/env python3
"""Send one credential-safe SMTP probe to Safari's own Gmail inbox only."""

from email.message import EmailMessage
from pathlib import Path
import os
import pwd
import smtplib
import ssl
import sys
from datetime import datetime, timezone
import re

ENV_FILE = Path("/srv/safari-tours-sharm/shared/.env.production")
ACCOUNT = "safaritourssharm@gmail.com"
NEEDED = (
    "TOURS_OPERATOR_NOTIFICATIONS_ENABLED",
    "TOURS_OPERATOR_NOTIFICATIONS_FROM",
    "TOURS_OPERATOR_NOTIFICATIONS_REPLY_TO",
    "SPRING_MAIL_HOST",
    "SPRING_MAIL_PORT",
    "SPRING_MAIL_USERNAME",
    "SPRING_MAIL_PASSWORD",
)


def fail(reason: str) -> None:
    print(f"Safari SMTP self-test stopped: {reason}", file=sys.stderr)
    raise SystemExit(1)


if ENV_FILE.is_symlink() or not ENV_FILE.is_file():
    fail("production env file missing or symlinked")
if pwd.getpwuid(os.geteuid()).pw_name != "resortos" or ENV_FILE.stat().st_uid != os.geteuid():
    fail("run as the Safari deploy user that owns the production env file")
if ENV_FILE.stat().st_mode & 0o077:
    fail("production env file permissions too broad")

values: dict[str, str] = {}
for line in ENV_FILE.read_text().splitlines():
    key, sep, value = line.partition("=")
    if sep and key in NEEDED:
        if key in values:
            fail(f"duplicate {key}")
        values[key] = value
if any(key not in values for key in NEEDED):
    fail("required mail settings missing")
if values["TOURS_OPERATOR_NOTIFICATIONS_ENABLED"] != "false":
    fail("notification dispatcher must be disabled for self-test")
if any(values[key] != ACCOUNT for key in (
    "TOURS_OPERATOR_NOTIFICATIONS_FROM",
    "TOURS_OPERATOR_NOTIFICATIONS_REPLY_TO",
    "SPRING_MAIL_USERNAME",
)):
    fail("sender, reply-to and username must be the Safari Gmail account")
if values["SPRING_MAIL_HOST"] != "smtp.gmail.com" or values["SPRING_MAIL_PORT"] != "587":
    fail("expected Gmail SMTP on port 587")
if re.fullmatch(r"[A-Za-z0-9]{16}", values["SPRING_MAIL_PASSWORD"]) is None:
    fail("App Password missing or invalid length")

message = EmailMessage()
message["From"] = ACCOUNT
message["To"] = ACCOUNT
message["Reply-To"] = ACCOUNT
message["Subject"] = "Safari Tours Sharm — SMTP self-test"
message.set_content(
    "This is a one-time technical delivery test to the Safari-owned inbox. "
    f"UTC: {datetime.now(timezone.utc).isoformat(timespec='seconds')}"
)

try:
    with smtplib.SMTP("smtp.gmail.com", 587, timeout=15) as smtp:
        smtp.ehlo()
        smtp.starttls(context=ssl.create_default_context())
        smtp.ehlo()
        smtp.login(ACCOUNT, values["SPRING_MAIL_PASSWORD"])
        refused = smtp.send_message(message)
except (OSError, smtplib.SMTPException) as exc:
    fail(f"SMTP delivery failed ({type(exc).__name__}); no credential details printed")

if refused:
    fail("Gmail refused the Safari-owned test recipient")
print("Gmail accepted the Safari self-test. Verify arrival in the Safari inbox.")
