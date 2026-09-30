-- WEGO-010-A Phase 1 (delivery/01_REQUEST_AND_BOOKING.md): the first real
-- transactional aggregate in this client. Everything before this migration
-- (V1-V4) is catalog master data and identity administration; nothing a
-- customer submits has ever been durably stored. See TravelRequest.kt's
-- class doc for the full transition table this schema enforces.

CREATE TABLE wego.travel_request (
    id uuid PRIMARY KEY,
    reference varchar(12) NOT NULL,
    service_id uuid NOT NULL REFERENCES wego.travel_service (id),
    service_option_id uuid NOT NULL,
    -- Snapshotted at creation from travel_service/travel_service_option, not
    -- a live FK read — a later catalog edit must never retroactively change
    -- what a customer was already shown or promised. See TravelRequest.kt.
    service_name_en text NOT NULL,
    service_name_ar text NOT NULL,
    option_label_en text NOT NULL,
    option_label_ar text NOT NULL,
    price_amount numeric(10, 2) NOT NULL,
    price_currency varchar(3) NOT NULL,
    price_basis varchar(16) NOT NULL,
    cancellation_policy_en text NOT NULL,
    cancellation_policy_ar text NOT NULL,
    requested_date date NOT NULL,
    requested_time time,
    adults integer NOT NULL,
    children integer NOT NULL DEFAULT 0,
    hotel_or_pickup text,
    locale varchar(2) NOT NULL,
    notes text,
    source_channel varchar(16) NOT NULL,
    customer_name text NOT NULL,
    customer_phone varchar(32),
    customer_email varchar(320),
    -- Client-supplied (or client-generated-and-retried) idempotency key —
    -- unique, so a duplicate submission (double-tap, retried request after a
    -- dropped response) returns the existing request instead of a second row.
    idempotency_key varchar(128) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'NEW',
    created_at timestamp with time zone NOT NULL,
    confirmed_at timestamp with time zone,
    completed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    cancel_reason varchar(32),
    cancel_detail text,
    expired_at timestamp with time zone,
    CONSTRAINT travel_request_reference_unique UNIQUE (reference),
    CONSTRAINT travel_request_idempotency_key_unique UNIQUE (idempotency_key),
    CONSTRAINT travel_request_reference_format
        CHECK (reference ~ '^STG-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$'),
    CONSTRAINT travel_request_service_name_en_not_blank CHECK (length(trim(service_name_en)) > 0),
    CONSTRAINT travel_request_service_name_ar_not_blank CHECK (length(trim(service_name_ar)) > 0),
    CONSTRAINT travel_request_option_label_en_not_blank CHECK (length(trim(option_label_en)) > 0),
    CONSTRAINT travel_request_option_label_ar_not_blank CHECK (length(trim(option_label_ar)) > 0),
    CONSTRAINT travel_request_price_nonnegative CHECK (price_amount >= 0),
    CONSTRAINT travel_request_price_basis_known
        CHECK (price_basis IN ('PER_PERSON', 'PER_GROUP', 'PER_VEHICLE', 'FLAT')),
    CONSTRAINT travel_request_cancellation_policy_en_not_blank CHECK (length(trim(cancellation_policy_en)) > 0),
    CONSTRAINT travel_request_cancellation_policy_ar_not_blank CHECK (length(trim(cancellation_policy_ar)) > 0),
    CONSTRAINT travel_request_adults_positive CHECK (adults >= 1),
    CONSTRAINT travel_request_children_nonnegative CHECK (children >= 0),
    CONSTRAINT travel_request_locale_known CHECK (locale IN ('en', 'ar')),
    CONSTRAINT travel_request_source_channel_known
        CHECK (source_channel IN ('WEBSITE', 'MOBILE')),
    CONSTRAINT travel_request_customer_name_not_blank CHECK (length(trim(customer_name)) > 0),
    CONSTRAINT travel_request_customer_contact_present
        CHECK (customer_phone IS NOT NULL OR customer_email IS NOT NULL),
    CONSTRAINT travel_request_idempotency_key_not_blank CHECK (length(trim(idempotency_key)) > 0),
    CONSTRAINT travel_request_status_known
        CHECK (status IN ('NEW', 'IN_REVIEW', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    -- Sticky "first confirmed at" marker — see TravelRequest.kt's init comment.
    -- A CONFIRMED-or-later status always has it set; CANCELLED/EXPIRED may or
    -- may not, depending on whether cancellation happened before or after
    -- confirmation, so only the forward direction is checked here.
    CONSTRAINT travel_request_confirmed_state
        CHECK (status NOT IN ('CONFIRMED', 'COMPLETED') OR confirmed_at IS NOT NULL),
    CONSTRAINT travel_request_completed_state
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CONSTRAINT travel_request_cancelled_state
        CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT travel_request_cancel_reason_state
        CHECK ((status = 'CANCELLED') = (cancel_reason IS NOT NULL)),
    CONSTRAINT travel_request_cancel_reason_known
        CHECK (cancel_reason IS NULL OR cancel_reason IN
            ('CUSTOMER_REQUESTED', 'STAFF_REJECTED', 'SERVICE_UNAVAILABLE', 'DUPLICATE_REQUEST', 'OTHER')),
    CONSTRAINT travel_request_expired_state
        CHECK ((status = 'EXPIRED') = (expired_at IS NOT NULL))
);

CREATE INDEX travel_request_service_idx
    ON wego.travel_request (service_id);

CREATE INDEX travel_request_status_idx
    ON wego.travel_request (status);

CREATE INDEX travel_request_created_at_idx
    ON wego.travel_request (created_at);

COMMENT ON TABLE wego.travel_request IS
    'A customer request — never a confirmed booking by itself. See TravelRequest.kt for the enforced state machine; delivery/01_REQUEST_AND_BOOKING.md for the product contract.';

CREATE TABLE wego.travel_request_audit_event (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES wego.travel_request (id) ON DELETE CASCADE,
    occurred_at timestamp with time zone NOT NULL,
    from_status varchar(16),
    to_status varchar(16) NOT NULL,
    actor_type varchar(16) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    reason varchar(32),
    detail text,
    correlation_id uuid,
    CONSTRAINT travel_request_audit_event_from_status_known
        CHECK (from_status IS NULL OR from_status IN ('NEW', 'IN_REVIEW', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT travel_request_audit_event_to_status_known
        CHECK (to_status IN ('NEW', 'IN_REVIEW', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT travel_request_audit_event_actor_type_known
        CHECK (actor_type IN ('CUSTOMER', 'STAFF', 'SYSTEM')),
    -- A STAFF actor must be attributable to a real account; CUSTOMER/SYSTEM
    -- actions have no staff user to attribute (the public endpoint has no
    -- authenticated identity, and a scheduled expiry sweep has no user at all).
    CONSTRAINT travel_request_audit_event_staff_actor_attributed
        CHECK (actor_type <> 'STAFF' OR actor_user_id IS NOT NULL)
);

CREATE INDEX travel_request_audit_event_request_idx
    ON wego.travel_request_audit_event (request_id);

CREATE INDEX travel_request_audit_event_correlation_idx
    ON wego.travel_request_audit_event (correlation_id)
    WHERE correlation_id IS NOT NULL;

INSERT INTO wego.identity_permission (code, description) VALUES
    ('travel-request:view', 'View travel requests (roster and full record).'),
    ('travel-request:review', 'Claim a new travel request for staff review.'),
    ('travel-request:confirm', 'Confirm a travel request into a commercial commitment.'),
    ('travel-request:cancel', 'Cancel a travel request at any non-terminal state.'),
    ('travel-request:complete', 'Mark a confirmed travel request as completed after the experience.');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'travel-request:view'),
    ('platform-admin', 'travel-request:review'),
    ('platform-admin', 'travel-request:confirm'),
    ('platform-admin', 'travel-request:cancel'),
    ('platform-admin', 'travel-request:complete');
