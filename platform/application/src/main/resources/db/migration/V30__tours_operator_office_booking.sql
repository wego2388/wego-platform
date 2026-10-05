-- ============================================================
-- V30 — Tours Operator: staff-created ("office") bookings
-- ============================================================
--
-- A booking now records the channel it came through. Every existing row is an
-- online booking, so the default keeps all history valid without a backfill.
--
-- An OFFICE booking is created by staff, takes places under the same slot row
-- lock as an online booking. Made in person, it is CONFIRMED at creation (no
-- online confirm, which needs a captured payment); what was paid is tracked by
-- the office ledger below (UNPAID until staff record money). The
-- owner allows office cash only; it is recorded in its own ledger below and
-- never as a Paymob payment. Because the 30-minute payment window is an
-- online-checkout rule, an OFFICE booking can never be EXPIRED
-- (enforced below as well as in code).
--
-- client_request_id makes a staff create retryable: the same actor re-sending
-- the same request id gets the booking already created, never a second one.

ALTER TABLE wego.tours_operator_booking
    ADD COLUMN channel varchar(16) NOT NULL DEFAULT 'ONLINE',
    ADD COLUMN created_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    ADD COLUMN client_request_id uuid;

ALTER TABLE wego.tours_operator_booking
    ADD CONSTRAINT tours_operator_booking_channel_known
        CHECK (channel IN ('ONLINE', 'OFFICE')),
    ADD CONSTRAINT tours_operator_booking_office_never_expires
        CHECK (channel = 'ONLINE' OR status <> 'EXPIRED'),
    ADD CONSTRAINT tours_operator_booking_staff_fields_office_only
        CHECK (channel = 'OFFICE' OR (created_by_user_id IS NULL AND client_request_id IS NULL)),
    ADD CONSTRAINT tours_operator_booking_office_has_request_id
        CHECK (channel <> 'OFFICE' OR client_request_id IS NOT NULL);

CREATE UNIQUE INDEX tours_operator_booking_office_request_unique
    ON wego.tours_operator_booking (created_by_user_id, client_request_id)
    WHERE client_request_id IS NOT NULL;

COMMENT ON COLUMN wego.tours_operator_booking.channel IS
    'ONLINE = public checkout (30-minute payment window); OFFICE = created by staff, unpaid until a collection method is decided, never auto-expired.';

-- The audit trail distinguishes an office creation (actor = staff user) from
-- a public one.
ALTER TABLE wego.tours_operator_booking_audit_event
    DROP CONSTRAINT tours_operator_booking_audit_event_type_known;

ALTER TABLE wego.tours_operator_booking_audit_event
    ADD CONSTRAINT tours_operator_booking_audit_event_type_known
        CHECK (event_type IN (
            'BOOKING_CREATED',
            'BOOKING_CREATED_OFFICE',
            'BOOKING_CONFIRMED',
            'BOOKING_CANCELLED',
            'BOOKING_COMPLETED',
            'BOOKING_EXPIRED'
        ));

-- ── Daily EUR→EGP rate (manager-set) ─────────────────────────
-- Office customers often pay in EGP while tours and balances are EUR. A
-- manager sets the day's rate; staff cannot type their own. Rows are
-- append-only: a new rate for the same day supersedes the earlier one (the
-- latest set_at wins) and the earlier rows stay as history.
-- Policy approved by the owner (2026-10-05).

CREATE TABLE wego.tours_operator_fx_rate (
    id               uuid PRIMARY KEY,
    rate_date        date NOT NULL,
    egp_per_eur      numeric(12, 4) NOT NULL,
    set_by_user_id   uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    set_at           timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_fx_rate_sane
        CHECK (egp_per_eur >= 1 AND egp_per_eur <= 1000)
);

CREATE INDEX tours_operator_fx_rate_date_idx
    ON wego.tours_operator_fx_rate (rate_date, set_at DESC);

COMMENT ON TABLE wego.tours_operator_fx_rate IS
    'Append-only history of manager-set EUR→EGP rates (EGP per 1 EUR). The latest row for a date is that day''s rate.';

-- ── Office collections ────────────────────────────────────────
-- Office customers pay staff directly: cash at the office or on pickup,
-- mobile wallet, the office card terminal, InstaPay or the office Fawry
-- machine. Staff record each payment by hand; there is no online
-- integration. A non-cash payment must carry the terminal/wallet/InstaPay/
-- Fawry receipt reference, unique per method so a receipt cannot be recorded
-- twice. Rows are append-only: a mistake is corrected only by a REVERSAL row
-- that points at the collection it cancels and carries a reason. This ledger
-- is deliberately separate from tours_operator_payment (Paymob): it never
-- feeds online revenue recognition.
--
-- amount_paid / currency_paid is what the customer handed over; amount_eur is
-- the EUR it settles against the booking balance (equal for EUR; for EGP it
-- is amount_paid / fx_rate rounded half-up to cents, with the rate used kept
-- on the row).

CREATE TABLE wego.tours_operator_office_collection (
    id                      uuid PRIMARY KEY,
    booking_id              uuid NOT NULL
                            REFERENCES wego.tours_operator_booking (id) ON DELETE RESTRICT,
    kind                    varchar(16) NOT NULL,
    method                  varchar(24) NOT NULL,
    currency_paid           varchar(3) NOT NULL,
    amount_paid             numeric(12, 2) NOT NULL,
    amount_eur              numeric(10, 2) NOT NULL,
    fx_rate                 numeric(12, 4),
    fx_rate_id              uuid REFERENCES wego.tours_operator_fx_rate (id) ON DELETE RESTRICT,
    reference               varchar(64),
    -- Set only on a COLLECTION that re-records a REVERSED collection with the
    -- same method and reference (the only way a receipt reference is reused).
    corrects_collection_id  uuid,
    reverses_collection_id  uuid REFERENCES wego.tours_operator_office_collection (id) ON DELETE RESTRICT,
    reason                  text,
    recorded_by_user_id     uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    client_request_id       uuid NOT NULL,
    recorded_at             timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_office_collection_kind_known
        CHECK (kind IN ('COLLECTION', 'REVERSAL')),
    CONSTRAINT tours_operator_office_collection_method_known
        CHECK (method IN ('CASH_AT_OFFICE', 'CASH_ON_PICKUP', 'MOBILE_WALLET',
                          'CARD_TERMINAL', 'INSTAPAY', 'FAWRY_OFFICE')),
    CONSTRAINT tours_operator_office_collection_currency_known
        CHECK (currency_paid IN ('EUR', 'EGP')),
    CONSTRAINT tours_operator_office_collection_amount_positive
        CHECK (amount_paid > 0 AND amount_eur > 0),
    CONSTRAINT tours_operator_office_collection_rate_matches_currency
        CHECK (
            (currency_paid = 'EUR' AND fx_rate IS NULL AND fx_rate_id IS NULL AND amount_paid = amount_eur)
            OR (currency_paid = 'EGP' AND fx_rate IS NOT NULL AND fx_rate > 0 AND fx_rate_id IS NOT NULL)
        ),
    CONSTRAINT tours_operator_office_collection_reference_shape
        CHECK (
            reference IS NULL
            OR (reference = btrim(reference)
                AND length(reference) BETWEEN 1 AND 64
                AND reference !~ '[[:cntrl:]]')
        ),
    -- Non-cash collections need the receipt reference; cash and reversals carry none.
    CONSTRAINT tours_operator_office_collection_reference_required
        CHECK (
            (kind = 'COLLECTION' AND method IN ('CASH_AT_OFFICE', 'CASH_ON_PICKUP') AND reference IS NULL)
            OR (kind = 'COLLECTION' AND method NOT IN ('CASH_AT_OFFICE', 'CASH_ON_PICKUP') AND reference IS NOT NULL)
            OR (kind = 'REVERSAL' AND reference IS NULL)
        ),
    CONSTRAINT tours_operator_office_collection_corrects_collection_only
        CHECK (corrects_collection_id IS NULL OR (kind = 'COLLECTION' AND reference IS NOT NULL)),
    -- Lets a correction reference the exact (id, method, reference) it re-records.
    CONSTRAINT tours_operator_office_collection_identity_unique
        UNIQUE (id, method, reference),
    CONSTRAINT tours_operator_office_collection_corrects_same_receipt
        FOREIGN KEY (corrects_collection_id, method, reference)
        REFERENCES wego.tours_operator_office_collection (id, method, reference),
    CONSTRAINT tours_operator_office_collection_reversal_shape
        CHECK (
            (kind = 'COLLECTION' AND reverses_collection_id IS NULL AND reason IS NULL)
            OR (kind = 'REVERSAL' AND reverses_collection_id IS NOT NULL
                AND reason IS NOT NULL AND length(trim(reason)) > 0)
        )
);

-- A collection can be reversed at most once.
CREATE UNIQUE INDEX tours_operator_office_collection_reversal_unique
    ON wego.tours_operator_office_collection (reverses_collection_id)
    WHERE reverses_collection_id IS NOT NULL;

-- The same terminal/wallet/InstaPay/Fawry receipt can never be recorded twice,
-- except by an explicit correction of a reversed entry (below).
CREATE UNIQUE INDEX tours_operator_office_collection_reference_unique
    ON wego.tours_operator_office_collection (method, reference)
    WHERE reference IS NOT NULL AND corrects_collection_id IS NULL;

-- A collection can be corrected once only.
CREATE UNIQUE INDEX tours_operator_office_collection_correction_unique
    ON wego.tours_operator_office_collection (corrects_collection_id)
    WHERE corrects_collection_id IS NOT NULL;

CREATE UNIQUE INDEX tours_operator_office_collection_request_unique
    ON wego.tours_operator_office_collection (recorded_by_user_id, client_request_id);

CREATE INDEX tours_operator_office_collection_booking_idx
    ON wego.tours_operator_office_collection (booking_id, recorded_at);

COMMENT ON TABLE wego.tours_operator_office_collection IS
    'Append-only office payment ledger (manual staff entries, EUR or EGP). Separate from Paymob payments and online revenue recognition; corrections are REVERSAL rows with a reason.';

INSERT INTO wego.identity_permission (code, description) VALUES
    ('tours-operator.booking:create-office', 'Create an office booking for a customer (unpaid until a payment is recorded)'),
    ('tours-operator.booking:collect-cash', 'Record or reverse office payments (cash, wallet, card terminal, InstaPay, Fawry) on an office booking'),
    ('tours-operator.booking:reverse-collection', 'Reverse an office payment recorded by someone else (manager)'),
    ('tours-operator.fx-rate:manage', 'Set the daily EUR→EGP rate used to settle EGP office payments');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'tours-operator.booking:create-office'),
    ('platform-admin', 'tours-operator.booking:collect-cash'),
    ('platform-admin', 'tours-operator.booking:reverse-collection'),
    ('platform-admin', 'tours-operator.fx-rate:manage');
