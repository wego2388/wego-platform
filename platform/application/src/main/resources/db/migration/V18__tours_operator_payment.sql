-- ============================================================
-- V18 — Tours Operator: Payment aggregate
-- ============================================================
-- One payment record per booking.  The booking owns the pricing
-- snapshot; the payment record owns the provider interaction,
-- idempotency, and financial state.
--
-- Design decisions:
--   * amount_eur / amount_minor_units are immutable once created.
--   * paymob_order_id is the external provider reference.
--   * paymob_transaction_id set only after provider confirms.
--   * idempotency_key  = bookingId (one payment per booking).
--   * All webhook/callback payloads stored verbatim for audit.
-- ============================================================

CREATE TABLE wego.tours_operator_payment (
    id                      uuid            PRIMARY KEY,
    booking_id              uuid            NOT NULL
                                REFERENCES wego.tours_operator_booking (id)
                                ON DELETE RESTRICT,
    -- Immutable amount snapshot from booking pricing at order creation time.
    amount_eur              numeric(10, 2)  NOT NULL,
    -- Minor-units copy for Paymob (cents × 100 = piasters in EGP, but EUR uses cents).
    -- Stored as bigint so reconciliation queries avoid decimal arithmetic.
    amount_minor_units      bigint          NOT NULL,
    currency_code           char(3)         NOT NULL DEFAULT 'EUR',
    -- Provider references
    paymob_order_id         varchar(64),
    paymob_transaction_id   varchar(64),
    -- Status machine: PENDING → PAID | FAILED | REFUNDED
    status                  varchar(16)     NOT NULL DEFAULT 'PENDING',
    -- Populated from Paymob webhook; NULL until callback arrives.
    provider_status         varchar(32),
    -- Raw HMAC-verified webhook payload stored verbatim for audit/reconciliation.
    last_callback_payload   text,
    -- Timestamps
    created_at              timestamp with time zone NOT NULL,
    paid_at                 timestamp with time zone,
    failed_at               timestamp with time zone,
    refunded_at             timestamp with time zone,

    CONSTRAINT tours_operator_payment_booking_unique
        UNIQUE (booking_id),
    CONSTRAINT tours_operator_payment_status_known
        CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    CONSTRAINT tours_operator_payment_currency_code_format
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT tours_operator_payment_amount_positive
        CHECK (amount_eur > 0),
    CONSTRAINT tours_operator_payment_minor_units_positive
        CHECK (amount_minor_units > 0),
    -- Timestamp consistency
    CONSTRAINT tours_operator_payment_paid_at_matches_status
        CHECK (
            (status = 'PAID'     AND paid_at     IS NOT NULL) OR
            (status <> 'PAID'    AND paid_at     IS NULL)
        ),
    CONSTRAINT tours_operator_payment_failed_at_matches_status
        CHECK (
            (status = 'FAILED'   AND failed_at   IS NOT NULL) OR
            (status <> 'FAILED'  AND failed_at   IS NULL)
        ),
    CONSTRAINT tours_operator_payment_refunded_at_matches_status
        CHECK (
            (status = 'REFUNDED' AND refunded_at IS NOT NULL) OR
            (status <> 'REFUNDED' AND refunded_at IS NULL)
        )
);

CREATE INDEX tours_operator_payment_booking_idx
    ON wego.tours_operator_payment (booking_id);

CREATE INDEX tours_operator_payment_status_idx
    ON wego.tours_operator_payment (status);

CREATE INDEX tours_operator_payment_paymob_order_idx
    ON wego.tours_operator_payment (paymob_order_id)
    WHERE paymob_order_id IS NOT NULL;

COMMENT ON TABLE wego.tours_operator_payment IS
    'One payment record per booking.  Immutable amount from booking snapshot.
     PENDING until Paymob webhook confirms; PAID on success; FAILED on decline/timeout;
     REFUNDED after refund webhook. paymob_order_id is set at order-creation time,
     paymob_transaction_id is set only after the first successful/failed callback.';

-- ── Permissions ───────────────────────────────────────────────────────────────

INSERT INTO wego.identity_permission (code, description) VALUES
    ('tours-operator.payment:view',    'View tours-operator payment records'),
    ('tours-operator.payment:refund',  'Initiate a refund for a tours-operator payment');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'tours-operator.payment:view'),
    ('platform-admin', 'tours-operator.payment:refund');
