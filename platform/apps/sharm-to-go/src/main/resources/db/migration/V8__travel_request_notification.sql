-- ============================================================
-- V8 - Travel marketplace: transactional notification outbox
-- ============================================================
--
-- One row per (request, kind), written in the same transaction as the request
-- transition that causes it, so a notification intent can neither be lost
-- nor duplicated (a replayed create/confirm/cancel hits the unique key and is
-- a no-op). A scheduled dispatcher claims due rows with FOR UPDATE SKIP
-- LOCKED, sends, and records the outcome.
--
-- PII-free by design: the customer address is read from travel_request at
-- send time and the staff address comes from configuration. Neither, nor any
-- message body, is ever stored here; last_error is a short code.

CREATE TABLE wego.travel_request_notification (
    id              uuid        PRIMARY KEY,
    request_id      uuid        NOT NULL
                        REFERENCES wego.travel_request (id) ON DELETE CASCADE,
    kind            varchar(32) NOT NULL,
    status          varchar(16) NOT NULL,
    attempt_count   integer     NOT NULL DEFAULT 0,
    available_at    timestamp with time zone NOT NULL,
    created_at      timestamp with time zone NOT NULL,
    sent_at         timestamp with time zone,
    -- Short reason code (error class or skip reason), never an address or body.
    last_error      varchar(200),
    -- Staff resends are audited: who asked last, when, and how many times.
    resend_count            integer NOT NULL DEFAULT 0,
    last_resent_by_user_id  uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    last_resent_at          timestamp with time zone,

    CONSTRAINT travel_request_notification_once_per_kind UNIQUE (request_id, kind),
    CONSTRAINT travel_request_notification_kind_known
        CHECK (kind IN ('STAFF_NEW_REQUEST', 'CUSTOMER_REQUEST_RECEIVED',
                        'CUSTOMER_REQUEST_CONFIRMED', 'CUSTOMER_REQUEST_CANCELLED')),
    CONSTRAINT travel_request_notification_status_known
        CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT travel_request_notification_counts_nonnegative
        CHECK (attempt_count >= 0 AND resend_count >= 0),
    CONSTRAINT travel_request_notification_sent_at_matches_status
        CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    -- Defence in depth for "no PII in the table": a reason code is a bare
    -- token (letters, digits, _ . -), so an address or message text cannot fit.
    CONSTRAINT travel_request_notification_last_error_is_code
        CHECK (last_error IS NULL OR last_error ~ '^[A-Za-z0-9_.-]*$')
);

CREATE INDEX travel_request_notification_due_idx
    ON wego.travel_request_notification (available_at)
    WHERE status = 'PENDING';

CREATE INDEX travel_request_notification_created_idx
    ON wego.travel_request_notification (created_at DESC, id);

COMMENT ON TABLE wego.travel_request_notification IS
    'Notification intents (staff new-request alert, customer received/confirmed/cancelled emails), one per request and kind. Delivered by a scheduled dispatcher with bounded retries; FAILED and SKIPPED rows stay visible to staff for resend. Holds no recipient address and no message body.';

-- ── Permissions ───────────────────────────────────────────────────────────────

INSERT INTO wego.identity_permission (code, description) VALUES
    ('travel-notification:manage', 'View delivery status of travel request notifications and resend them.');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'travel-notification:manage');
