-- ============================================================
-- V22 — Tours Operator: transactional customer notifications
-- ============================================================
--
-- One row per (booking, kind), written in the same transaction as the
-- booking transition that causes it, so a notification intent can neither be
-- lost nor duplicated. A scheduled dispatcher claims due rows with
-- FOR UPDATE SKIP LOCKED, sends, and records the outcome. The recipient
-- address is read from the booking at send time and is never copied here.

CREATE TABLE wego.tours_operator_notification (
    id              uuid        PRIMARY KEY,
    booking_id      uuid        NOT NULL
                        REFERENCES wego.tours_operator_booking (id) ON DELETE CASCADE,
    kind            varchar(32) NOT NULL,
    status          varchar(16) NOT NULL,
    attempt_count   integer     NOT NULL DEFAULT 0,
    available_at    timestamp with time zone NOT NULL,
    created_at      timestamp with time zone NOT NULL,
    sent_at         timestamp with time zone,
    -- Short, PII-free reason (error class or skip reason), never a message body.
    last_error      varchar(200),
    -- Staff resends are audited: who asked last, when, and how many times.
    resend_count            integer NOT NULL DEFAULT 0,
    last_resent_by_user_id  uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    last_resent_at          timestamp with time zone,

    CONSTRAINT tours_operator_notification_once_per_kind UNIQUE (booking_id, kind),
    CONSTRAINT tours_operator_notification_kind_known
        CHECK (kind IN ('BOOKING_CONFIRMED', 'BOOKING_CANCELLED', 'REVIEW_REQUEST')),
    CONSTRAINT tours_operator_notification_status_known
        CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT tours_operator_notification_attempts_nonnegative
        CHECK (attempt_count >= 0 AND resend_count >= 0),
    CONSTRAINT tours_operator_notification_sent_at_matches_status
        CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

CREATE INDEX tours_operator_notification_due_idx
    ON wego.tours_operator_notification (available_at)
    WHERE status = 'PENDING';

CREATE INDEX tours_operator_notification_created_idx
    ON wego.tours_operator_notification (created_at DESC, id);

COMMENT ON TABLE wego.tours_operator_notification IS
    'Customer notification intents (confirmation, cancellation, review request), one per booking and kind. Delivered by a scheduled dispatcher with bounded retries; FAILED and SKIPPED rows stay visible to staff for resend.';

-- ── Permissions ───────────────────────────────────────────────────────────────

INSERT INTO wego.identity_permission (code, description) VALUES
    ('tours-operator.notification:manage', 'Resend customer notifications for tours-operator bookings');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'tours-operator.notification:manage');
