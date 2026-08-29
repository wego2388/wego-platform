CREATE TABLE wego.divers_offering (
    id uuid PRIMARY KEY,
    offering_type varchar(32) NOT NULL,
    title text NOT NULL,
    description text,
    starts_on date NOT NULL,
    ends_on date,
    capacity integer,
    pricing_basis varchar(16) NOT NULL,
    unit_price numeric(10, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL,
    closed_at timestamp with time zone,
    CONSTRAINT divers_offering_type_known
        CHECK (offering_type IN ('DIVE_TRIP', 'COURSE', 'EQUIPMENT_RENTAL', 'PACKAGE')),
    CONSTRAINT divers_offering_title_not_blank
        CHECK (length(trim(title)) > 0),
    CONSTRAINT divers_offering_ends_on_after_starts_on
        CHECK (ends_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT divers_offering_capacity_positive
        CHECK (capacity IS NULL OR capacity > 0),
    -- PER_PARTICIPANT: a booking's total is unit_price * party size (a
    -- per-diver trip/course/package). FLAT: the total is unit_price
    -- regardless of party size (e.g. a single equipment-rental line, a
    -- whole-boat charter). Explicit on every offering — never inferred
    -- from offering_type — so a booking's price snapshot is unambiguous.
    CONSTRAINT divers_offering_pricing_basis_known
        CHECK (pricing_basis IN ('PER_PARTICIPANT', 'FLAT')),
    CONSTRAINT divers_offering_unit_price_nonnegative
        CHECK (unit_price >= 0),
    CONSTRAINT divers_offering_currency_code_format
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT divers_offering_status_known
        CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT divers_offering_closed_at_matches_status
        CHECK (
            (status = 'CLOSED' AND closed_at IS NOT NULL)
            OR (status = 'ACTIVE' AND closed_at IS NULL)
        )
);

CREATE INDEX divers_offering_starts_on_idx
    ON wego.divers_offering (starts_on);

CREATE INDEX divers_offering_active_idx
    ON wego.divers_offering (status)
    WHERE status = 'ACTIVE';

CREATE TABLE wego.divers_offering_audit_event (
    id uuid PRIMARY KEY,
    offering_id uuid NOT NULL REFERENCES wego.divers_offering (id) ON DELETE CASCADE,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(32) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    reason text,
    correlation_id uuid,
    CONSTRAINT divers_offering_audit_event_type_known
        CHECK (event_type IN ('OFFERING_CREATED', 'OFFERING_CLOSED'))
);

CREATE INDEX divers_offering_audit_event_offering_idx
    ON wego.divers_offering_audit_event (offering_id);

CREATE TABLE wego.divers_booking (
    id uuid PRIMARY KEY,
    offering_id uuid NOT NULL REFERENCES wego.divers_offering (id) ON DELETE RESTRICT,
    party_size integer NOT NULL,
    customer_name text NOT NULL,
    customer_email varchar(320),
    customer_phone varchar(32),
    status varchar(16) NOT NULL,
    payment_status varchar(16) NOT NULL DEFAULT 'UNPAID',
    -- Snapshot of the offering's pricing at booking time, not a live
    -- reference — a later change to the offering's unit_price must never
    -- retroactively change an existing booking's total.
    pricing_basis varchar(16) NOT NULL,
    unit_price numeric(10, 2) NOT NULL,
    billable_quantity integer NOT NULL,
    total_price numeric(10, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    -- SHA-256 hex digest of the canonical request (offeringId, partySize,
    -- normalized customer name/email/phone) this key was first used with —
    -- see BookingFingerprint.of. Lets a true retry (same fingerprint) replay
    -- safely while a key reused with different parameters is rejected
    -- instead of silently returning a stale booking.
    idempotency_fingerprint varchar(64) NOT NULL,
    created_by_user_id uuid NOT NULL REFERENCES wego.identity_user (id) ON DELETE RESTRICT,
    created_at timestamp with time zone NOT NULL,
    cancelled_at timestamp with time zone,
    cancellation_reason text,
    CONSTRAINT divers_booking_party_size_positive
        CHECK (party_size > 0),
    CONSTRAINT divers_booking_customer_name_not_blank
        CHECK (length(trim(customer_name)) > 0),
    CONSTRAINT divers_booking_customer_contact_present
        CHECK (
            (customer_email IS NOT NULL AND length(trim(customer_email)) > 0)
            OR (customer_phone IS NOT NULL AND length(trim(customer_phone)) > 0)
        ),
    CONSTRAINT divers_booking_status_known
        CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT divers_booking_payment_status_known
        CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED')),
    CONSTRAINT divers_booking_pricing_basis_known
        CHECK (pricing_basis IN ('PER_PARTICIPANT', 'FLAT')),
    CONSTRAINT divers_booking_unit_price_nonnegative
        CHECK (unit_price >= 0),
    CONSTRAINT divers_booking_billable_quantity_positive
        CHECK (billable_quantity > 0),
    -- Mirrors BookingPricing.forOffering: PER_PARTICIPANT bills exactly the
    -- party size, FLAT always bills exactly 1. The application always
    -- computes this correctly, but the same belt-and-suspenders reasoning
    -- as divers_booking_total_price_matches_basis below applies — a future
    -- code path miscomputing billable_quantity should fail loudly at
    -- insert time, not silently persist an inconsistent row.
    CONSTRAINT divers_booking_billable_quantity_matches_basis
        CHECK (
            (pricing_basis = 'PER_PARTICIPANT' AND billable_quantity = party_size)
            OR (pricing_basis = 'FLAT' AND billable_quantity = 1)
        ),
    CONSTRAINT divers_booking_total_price_nonnegative
        CHECK (total_price >= 0),
    -- Exact NUMERIC arithmetic (no binary float involved), so this is a
    -- real integrity guarantee, not an approximation.
    CONSTRAINT divers_booking_total_price_matches_basis
        CHECK (total_price = unit_price * billable_quantity),
    CONSTRAINT divers_booking_currency_code_format
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT divers_booking_idempotency_key_length
        CHECK (length(idempotency_key) BETWEEN 1 AND 128),
    CONSTRAINT divers_booking_idempotency_fingerprint_format
        CHECK (idempotency_fingerprint ~ '^[a-f0-9]{64}$'),
    CONSTRAINT divers_booking_cancelled_at_matches_status
        CHECK (
            (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND length(trim(cancellation_reason)) > 0)
            OR (status = 'CONFIRMED' AND cancelled_at IS NULL AND cancellation_reason IS NULL)
        ),
    CONSTRAINT divers_booking_idempotency_key_unique
        UNIQUE (created_by_user_id, idempotency_key)
);

CREATE INDEX divers_booking_offering_idx
    ON wego.divers_booking (offering_id);

CREATE INDEX divers_booking_offering_confirmed_idx
    ON wego.divers_booking (offering_id)
    WHERE status = 'CONFIRMED';

-- Backs the paginated, unfiltered `GET /api/v1/divers/bookings` listing's
-- `ORDER BY created_at DESC` — this table is append-only (cancel is a
-- status flip, never a delete), so it only grows over the client's life.
CREATE INDEX divers_booking_created_at_idx
    ON wego.divers_booking (created_at DESC);

CREATE TABLE wego.divers_booking_audit_event (
    id uuid PRIMARY KEY,
    booking_id uuid NOT NULL REFERENCES wego.divers_booking (id) ON DELETE CASCADE,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(32) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    from_status varchar(16),
    to_status varchar(16),
    reason text,
    correlation_id uuid,
    CONSTRAINT divers_booking_audit_event_type_known
        CHECK (event_type IN ('BOOKING_CREATED', 'BOOKING_CANCELLED', 'PAYMENT_MARKED_PAID', 'PAYMENT_REFUNDED'))
);

CREATE INDEX divers_booking_audit_event_booking_idx
    ON wego.divers_booking_audit_event (booking_id);

COMMENT ON TABLE wego.divers_offering IS
    'A manually created, already-dated bookable instance (trip, course, rental, or package). Not a recurring template.';
COMMENT ON TABLE wego.divers_booking IS
    'A single-offering booking with a denormalized customer contact snapshot and an immutable pricing snapshot captured at creation time. Confirms immediately; CONFIRMED and CANCELLED are the only states.';
COMMENT ON TABLE wego.divers_booking_audit_event IS
    'Append-only record of booking lifecycle transitions.';
COMMENT ON TABLE wego.divers_offering_audit_event IS
    'Append-only record of offering lifecycle transitions.';

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'offering:manage'),
    ('platform-admin', 'offering:view'),
    ('platform-admin', 'booking:create'),
    ('platform-admin', 'booking:view'),
    ('platform-admin', 'booking:cancel'),
    ('platform-admin', 'booking:payment-update'),
    ('platform-admin', 'booking:refund');
