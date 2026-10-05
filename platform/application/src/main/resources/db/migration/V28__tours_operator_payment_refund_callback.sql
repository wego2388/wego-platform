-- V28 — durable identity for provider refund callbacks
--
-- Paymob can deliver more than one partial-refund callback. The payment row's
-- last_callback_audit is intentionally only a current snapshot; it cannot
-- identify A, B, then a replay of A. This table makes each provider refund
-- transaction claimable exactly once per payment.

CREATE TABLE wego.tours_operator_payment_refund_event (
    id                   uuid PRIMARY KEY,
    payment_id           uuid NOT NULL
                         REFERENCES wego.tours_operator_payment (id) ON DELETE CASCADE,
    provider_refund_id   varchar(80) NOT NULL,
    amount_minor_units   bigint NOT NULL,
    received_at          timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_payment_refund_amount_positive
        CHECK (amount_minor_units > 0),
    CONSTRAINT tours_operator_payment_refund_identity_unique
        UNIQUE (payment_id, provider_refund_id)
);

CREATE INDEX tours_operator_payment_refund_payment_idx
    ON wego.tours_operator_payment_refund_event (payment_id, received_at);

COMMENT ON TABLE wego.tours_operator_payment_refund_event IS
    'Durable first-seen identities for provider refund callbacks; prevents duplicate review events when callbacks arrive out of order.';
