-- ============================================================
-- V14 — Tours Operator: tour catalog, availability, bookings
-- ============================================================
-- Isolated product boundary: all tables prefixed tours_operator_
-- No foreign keys into divers_* or travel_marketplace_* tables.
-- Booking reference pattern: STR-YYYY-NNNN (sequence per year).
-- Monetary amounts stored as integer cents (bigint) in the catalog;
-- booking price snapshot uses numeric(10,2) EUR to match Money type.
-- ============================================================

-- ── Tour catalog ─────────────────────────────────────────────

CREATE TABLE wego.tours_operator_tour (
    id                  uuid            PRIMARY KEY,
    slug                varchar(80)     NOT NULL,
    category            varchar(16)     NOT NULL,
    duration_text       text            NOT NULL,
    -- Prices stored in EUR cents (integer) to avoid floating-point at rest.
    price_adult_cents   bigint          NOT NULL,
    price_child_cents   bigint,
    capacity            integer         NOT NULL,
    -- Comma-separated set of TimeSlot values; small fixed set, no join table needed.
    available_time_slots text           NOT NULL,
    sort_order          integer         NOT NULL DEFAULT 0,
    is_active           boolean         NOT NULL DEFAULT false,
    created_by_user_id  uuid            REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at          timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_tour_slug_unique
        UNIQUE (slug),
    CONSTRAINT tours_operator_tour_slug_format
        CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$'),
    CONSTRAINT tours_operator_tour_category_known
        CHECK (category IN ('DESERT', 'SEA', 'CULTURAL', 'SHOWS', 'TRANSFERS')),
    CONSTRAINT tours_operator_tour_duration_text_not_blank
        CHECK (length(trim(duration_text)) > 0),
    CONSTRAINT tours_operator_tour_price_adult_nonnegative
        CHECK (price_adult_cents >= 0),
    CONSTRAINT tours_operator_tour_price_child_nonnegative
        CHECK (price_child_cents IS NULL OR price_child_cents >= 0),
    CONSTRAINT tours_operator_tour_capacity_positive
        CHECK (capacity >= 1),
    CONSTRAINT tours_operator_tour_sort_order_nonnegative
        CHECK (sort_order >= 0)
);

CREATE INDEX tours_operator_tour_category_active_idx
    ON wego.tours_operator_tour (category, is_active);

CREATE INDEX tours_operator_tour_sort_order_idx
    ON wego.tours_operator_tour (sort_order, id);

COMMENT ON TABLE wego.tours_operator_tour IS
    'Safari Tours Sharm tour catalog. is_active=false means draft/hidden; slug is the public URL key.';

-- ── Availability slots ────────────────────────────────────────

CREATE TABLE wego.tours_operator_tour_slot (
    id              uuid        PRIMARY KEY,
    tour_id         uuid        NOT NULL REFERENCES wego.tours_operator_tour (id) ON DELETE CASCADE,
    date            date        NOT NULL,
    time_slot       varchar(16) NOT NULL,
    capacity        integer     NOT NULL,
    booked_count    integer     NOT NULL DEFAULT 0,
    is_blocked      boolean     NOT NULL DEFAULT false,
    created_at      timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_slot_unique
        UNIQUE (tour_id, date, time_slot),
    CONSTRAINT tours_operator_slot_time_slot_known
        CHECK (time_slot IN ('SUNRISE', 'MORNING', 'AFTERNOON', 'SUNSET')),
    CONSTRAINT tours_operator_slot_capacity_positive
        CHECK (capacity >= 1),
    CONSTRAINT tours_operator_slot_booked_count_nonnegative
        CHECK (booked_count >= 0),
    CONSTRAINT tours_operator_slot_booked_count_not_exceed_capacity
        CHECK (booked_count <= capacity)
);

CREATE INDEX tours_operator_slot_tour_date_idx
    ON wego.tours_operator_tour_slot (tour_id, date);

CREATE INDEX tours_operator_slot_available_idx
    ON wego.tours_operator_tour_slot (tour_id, date)
    WHERE is_blocked = false AND booked_count < capacity;

COMMENT ON TABLE wego.tours_operator_tour_slot IS
    'One bookable slot per (tour, date, time_slot). booked_count is authoritative for capacity checks.';

-- ── Booking reference sequence (per year) ────────────────────

CREATE TABLE wego.tours_operator_booking_reference_seq (
    year        integer     NOT NULL,
    next_val    bigint      NOT NULL DEFAULT 1,
    PRIMARY KEY (year)
);

COMMENT ON TABLE wego.tours_operator_booking_reference_seq IS
    'Per-year counter for STR-YYYY-NNNN booking references. next_val is claimed with SELECT ... FOR UPDATE.';

-- ── Bookings ──────────────────────────────────────────────────

CREATE TABLE wego.tours_operator_booking (
    id                  uuid            PRIMARY KEY,
    -- Public human-readable reference: STR-YYYY-NNNN
    reference           varchar(32)     NOT NULL,
    tour_id             uuid            NOT NULL REFERENCES wego.tours_operator_tour (id) ON DELETE RESTRICT,
    slot_id             uuid            NOT NULL REFERENCES wego.tours_operator_tour_slot (id) ON DELETE RESTRICT,
    tour_date           date            NOT NULL,
    time_slot           varchar(16)     NOT NULL,
    -- Pricing snapshot — immutable after creation
    adults_count        integer         NOT NULL,
    children_count      integer         NOT NULL DEFAULT 0,
    price_adult_eur     numeric(10, 2)  NOT NULL,
    price_child_eur     numeric(10, 2),
    total_eur           numeric(10, 2)  NOT NULL,
    -- Customer snapshot
    customer_full_name  text            NOT NULL,
    customer_phone      varchar(32)     NOT NULL,
    customer_nationality char(2)        NOT NULL,
    customer_email      varchar(320),
    hotel_name          text            NOT NULL,
    hotel_room          varchar(32),
    special_requests    text,
    locale              varchar(8)      NOT NULL,
    status              varchar(16)     NOT NULL DEFAULT 'NEW',
    created_at          timestamp with time zone NOT NULL,
    confirmed_at        timestamp with time zone,
    cancelled_at        timestamp with time zone,
    cancellation_reason text,
    completed_at        timestamp with time zone,
    expired_at          timestamp with time zone,

    CONSTRAINT tours_operator_booking_reference_unique
        UNIQUE (reference),
    CONSTRAINT tours_operator_booking_reference_format
        CHECK (reference ~ '^STR-\d{4}-\d+$'),
    CONSTRAINT tours_operator_booking_time_slot_known
        CHECK (time_slot IN ('SUNRISE', 'MORNING', 'AFTERNOON', 'SUNSET')),
    CONSTRAINT tours_operator_booking_adults_count_positive
        CHECK (adults_count >= 1),
    CONSTRAINT tours_operator_booking_children_count_nonnegative
        CHECK (children_count >= 0),
    CONSTRAINT tours_operator_booking_price_adult_nonnegative
        CHECK (price_adult_eur >= 0),
    CONSTRAINT tours_operator_booking_price_child_nonnegative
        CHECK (price_child_eur IS NULL OR price_child_eur >= 0),
    CONSTRAINT tours_operator_booking_total_nonnegative
        CHECK (total_eur >= 0),
    -- Exact arithmetic check — prevents any code path from persisting an
    -- inconsistent total without raising a clean constraint violation.
    CONSTRAINT tours_operator_booking_total_matches_pricing
        CHECK (
            total_eur = (price_adult_eur * adults_count)
                      + (COALESCE(price_child_eur, 0) * children_count)
        ),
    CONSTRAINT tours_operator_booking_customer_full_name_not_blank
        CHECK (length(trim(customer_full_name)) > 0),
    CONSTRAINT tours_operator_booking_customer_phone_not_blank
        CHECK (length(trim(customer_phone)) > 0),
    CONSTRAINT tours_operator_booking_nationality_format
        CHECK (customer_nationality ~ '^[A-Z]{2}$'),
    CONSTRAINT tours_operator_booking_hotel_name_not_blank
        CHECK (length(trim(hotel_name)) > 0),
    CONSTRAINT tours_operator_booking_locale_format
        CHECK (locale ~ '^[a-z]{2,3}$'),
    CONSTRAINT tours_operator_booking_status_known
        CHECK (status IN ('NEW', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    -- Status/timestamp consistency mirrors divers_booking pattern.
    CONSTRAINT tours_operator_booking_confirmed_at_matches_status
        CHECK (
            -- confirmed_at is a historical timestamp: set when a booking first
            -- reaches CONFIRMED and never cleared on subsequent transitions.
            -- Only NEW and EXPIRED bookings (never reached payment) may have NULL.
            (status IN ('NEW', 'EXPIRED') AND confirmed_at IS NULL)
            OR (status NOT IN ('NEW', 'EXPIRED') AND confirmed_at IS NOT NULL)
            OR (status = 'CANCELLED' AND confirmed_at IS NULL)  -- cancelled before payment
        ),
    CONSTRAINT tours_operator_booking_cancelled_at_matches_status
        CHECK (
            (status = 'CANCELLED'
             AND cancelled_at IS NOT NULL
             AND length(trim(cancellation_reason)) > 0)
            OR (status <> 'CANCELLED'
             AND cancelled_at IS NULL
             AND cancellation_reason IS NULL)
        ),
    CONSTRAINT tours_operator_booking_completed_at_matches_status
        CHECK (
            (status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR (status <> 'COMPLETED' AND completed_at IS NULL)
        ),
    CONSTRAINT tours_operator_booking_expired_at_matches_status
        CHECK (
            (status = 'EXPIRED' AND expired_at IS NOT NULL)
            OR (status <> 'EXPIRED' AND expired_at IS NULL)
        )
);

CREATE INDEX tours_operator_booking_tour_date_idx
    ON wego.tours_operator_booking (tour_id, tour_date);

CREATE INDEX tours_operator_booking_slot_idx
    ON wego.tours_operator_booking (slot_id);

CREATE INDEX tours_operator_booking_status_idx
    ON wego.tours_operator_booking (status);

CREATE INDEX tours_operator_booking_created_at_idx
    ON wego.tours_operator_booking (created_at DESC);

-- Backs the customer lookup endpoint (reference + phone).
CREATE INDEX tours_operator_booking_reference_phone_idx
    ON wego.tours_operator_booking (reference, customer_phone);

-- NEW bookings pending payment — used by the expiry scheduler.
CREATE INDEX tours_operator_booking_new_created_at_idx
    ON wego.tours_operator_booking (created_at)
    WHERE status = 'NEW';

COMMENT ON TABLE wego.tours_operator_booking IS
    'Customer bookings with immutable pricing snapshot. NEW awaits payment (30 min window); CONFIRMED means paid. Reference STR-YYYY-NNNN is the public identifier.';

-- ── Booking audit log ─────────────────────────────────────────

CREATE TABLE wego.tours_operator_booking_audit_event (
    id              uuid        PRIMARY KEY,
    booking_id      uuid        NOT NULL REFERENCES wego.tours_operator_booking (id) ON DELETE CASCADE,
    event_type      varchar(32) NOT NULL,
    from_status     varchar(16),
    to_status       varchar(16),
    reason          text,
    actor_user_id   uuid        REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    occurred_at     timestamp with time zone NOT NULL,
    correlation_id  uuid,

    CONSTRAINT tours_operator_booking_audit_event_type_known
        CHECK (event_type IN (
            'BOOKING_CREATED',
            'BOOKING_CONFIRMED',
            'BOOKING_CANCELLED',
            'BOOKING_COMPLETED',
            'BOOKING_EXPIRED'
        ))
);

CREATE INDEX tours_operator_booking_audit_booking_idx
    ON wego.tours_operator_booking_audit_event (booking_id, occurred_at DESC);

COMMENT ON TABLE wego.tours_operator_booking_audit_event IS
    'Append-only audit trail for every booking lifecycle transition.';

-- ── Permissions & role grants ─────────────────────────────────
-- V9 introduced wego.identity_permission as the canonical registry for all
-- permission codes, with a FK from identity_role_permission(permission_code).
-- Every new permission code must be inserted into identity_permission first.

INSERT INTO wego.identity_permission (code, description) VALUES
    ('tours-operator.booking:view',           'View tours-operator bookings'),
    ('tours-operator.booking:cancel',         'Cancel a tours-operator booking'),
    ('tours-operator.booking:complete',       'Mark a tours-operator booking as completed'),
    ('tours-operator.booking:payment-update', 'Update payment status on a tours-operator booking'),
    ('tours-operator.tour:manage',            'Create, update, and deactivate tours'),
    ('tours-operator.tour:view',              'View tours and their details'),
    ('tours-operator.slot:manage',            'Create, update, and block tour slots');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'tours-operator.booking:view'),
    ('platform-admin', 'tours-operator.booking:cancel'),
    ('platform-admin', 'tours-operator.booking:complete'),
    ('platform-admin', 'tours-operator.booking:payment-update'),
    ('platform-admin', 'tours-operator.tour:manage'),
    ('platform-admin', 'tours-operator.tour:view'),
    ('platform-admin', 'tours-operator.slot:manage');
