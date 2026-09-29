-- ============================================================
-- V19 — Tours Operator: harden payment/provider state
-- ============================================================

ALTER TABLE wego.tours_operator_payment
    RENAME COLUMN last_callback_payload TO last_callback_audit;

ALTER TABLE wego.tours_operator_payment
    ADD COLUMN provider_checkout_token text,
    ADD COLUMN provider_reference varchar(80);

-- A provider call cannot be atomic with PostgreSQL. Persist a stable merchant
-- reference before calling Paymob so an ambiguous/crashed initiation can be
-- reconciled without creating a second payable intention.
UPDATE wego.tours_operator_payment
SET provider_reference = 'sts-' || id::text;

ALTER TABLE wego.tours_operator_payment
    ALTER COLUMN provider_reference SET NOT NULL;

ALTER TABLE wego.tours_operator_payment
    ADD CONSTRAINT tours_operator_payment_provider_reference_unique
        UNIQUE (provider_reference);

COMMENT ON COLUMN wego.tours_operator_payment.last_callback_audit IS
    'Minimized, HMAC-verified callback audit JSON. Never contains billing data, PAN fragments, email, phone, address, or the original callback body.';

COMMENT ON COLUMN wego.tours_operator_payment.provider_checkout_token IS
    'Short-lived Paymob checkout client secret. Cleared when the payment reaches a terminal state and never written to logs.';

COMMENT ON COLUMN wego.tours_operator_payment.provider_reference IS
    'Stable Wego-generated merchant reference persisted before any Paymob call. Used for reconciliation after an ambiguous provider outcome.';

-- V18 did not enforce provider-reference uniqueness. Preserve every duplicate
-- value for explicit operator reconciliation, then clear the ambiguous active
-- references before adding uniqueness. This lets a real V18 database upgrade
-- deterministically instead of failing halfway through CREATE UNIQUE INDEX.
CREATE TABLE wego.tours_operator_payment_reference_quarantine (
    reference_type      varchar(32) NOT NULL,
    reference_value     varchar(128) NOT NULL,
    payment_id          uuid NOT NULL
                            REFERENCES wego.tours_operator_payment (id)
                            ON DELETE RESTRICT,
    reason              varchar(64) NOT NULL,
    quarantined_at      timestamp with time zone NOT NULL DEFAULT now(),

    PRIMARY KEY (reference_type, reference_value, payment_id),
    CONSTRAINT tours_operator_payment_quarantine_type_known
        CHECK (reference_type IN ('PAYMOB_ORDER_ID', 'PAYMOB_TRANSACTION_ID'))
);

INSERT INTO wego.tours_operator_payment_reference_quarantine
    (reference_type, reference_value, payment_id, reason)
SELECT 'PAYMOB_ORDER_ID', payment.paymob_order_id, payment.id, 'DUPLICATE_V18_REFERENCE'
FROM wego.tours_operator_payment payment
JOIN (
    SELECT paymob_order_id
    FROM wego.tours_operator_payment
    WHERE paymob_order_id IS NOT NULL
    GROUP BY paymob_order_id
    HAVING count(*) > 1
) duplicate_ref ON duplicate_ref.paymob_order_id = payment.paymob_order_id;

UPDATE wego.tours_operator_payment payment
SET paymob_order_id = NULL,
    provider_checkout_token = NULL
WHERE payment.paymob_order_id IN (
    SELECT paymob_order_id
    FROM wego.tours_operator_payment
    WHERE paymob_order_id IS NOT NULL
    GROUP BY paymob_order_id
    HAVING count(*) > 1
);

INSERT INTO wego.tours_operator_payment_reference_quarantine
    (reference_type, reference_value, payment_id, reason)
SELECT 'PAYMOB_TRANSACTION_ID', payment.paymob_transaction_id, payment.id, 'DUPLICATE_V18_REFERENCE'
FROM wego.tours_operator_payment payment
JOIN (
    SELECT paymob_transaction_id
    FROM wego.tours_operator_payment
    WHERE paymob_transaction_id IS NOT NULL
    GROUP BY paymob_transaction_id
    HAVING count(*) > 1
) duplicate_ref ON duplicate_ref.paymob_transaction_id = payment.paymob_transaction_id;

UPDATE wego.tours_operator_payment payment
SET paymob_transaction_id = NULL
WHERE payment.paymob_transaction_id IN (
    SELECT paymob_transaction_id
    FROM wego.tours_operator_payment
    WHERE paymob_transaction_id IS NOT NULL
    GROUP BY paymob_transaction_id
    HAVING count(*) > 1
);

COMMENT ON TABLE wego.tours_operator_payment_reference_quarantine IS
    'Provider references that were non-unique before V19. Rows require explicit reconciliation and are never used for automatic webhook routing.';

ALTER TABLE wego.tours_operator_payment
    DROP CONSTRAINT tours_operator_payment_status_known;

ALTER TABLE wego.tours_operator_payment
    DROP CONSTRAINT tours_operator_payment_paid_at_matches_status;

ALTER TABLE wego.tours_operator_payment
    ALTER COLUMN status TYPE varchar(32);

ALTER TABLE wego.tours_operator_payment
    ADD CONSTRAINT tours_operator_payment_status_known
        CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED', 'REVIEW_REQUIRED', 'RECONCILIATION_REQUIRED')),
    ADD CONSTRAINT tours_operator_payment_paid_at_matches_status
        CHECK (
            (status IN ('PAID', 'REFUNDED', 'REVIEW_REQUIRED') AND paid_at IS NOT NULL) OR
            (status NOT IN ('PAID', 'REFUNDED', 'REVIEW_REQUIRED') AND paid_at IS NULL)
        );

DROP INDEX wego.tours_operator_payment_paymob_order_idx;

CREATE UNIQUE INDEX tours_operator_payment_paymob_order_unique
    ON wego.tours_operator_payment (paymob_order_id)
    WHERE paymob_order_id IS NOT NULL;

CREATE UNIQUE INDEX tours_operator_payment_paymob_transaction_unique
    ON wego.tours_operator_payment (paymob_transaction_id)
    WHERE paymob_transaction_id IS NOT NULL;
