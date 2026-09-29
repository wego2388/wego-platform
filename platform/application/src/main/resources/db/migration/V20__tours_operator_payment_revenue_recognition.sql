-- ============================================================
-- V20 — Tours Operator: persist revenue recognition of a capture
-- ============================================================
--
-- status is overwritten on refund, so a REFUNDED row cannot tell whether its
-- capture was ever recognised as revenue (PAID) or was held for review
-- (REVIEW_REQUIRED, e.g. captured after the booking expired). Without this
-- column a refund silently restated a closed period by turning an
-- unrecognised capture into revenue on its original paid_at.

ALTER TABLE wego.tours_operator_payment
    ADD COLUMN revenue_recognised_at timestamp with time zone;

-- Pre-V20 PAID rows were recognised when captured. Pre-V20 REFUNDED rows are
-- ambiguous; they stay unrecognised (NULL) so finance never counts a capture
-- it cannot prove was accepted. The first launch has no production payments.
UPDATE wego.tours_operator_payment
SET revenue_recognised_at = paid_at
WHERE status = 'PAID';

ALTER TABLE wego.tours_operator_payment
    ADD CONSTRAINT tours_operator_payment_recognised_capture
        CHECK (
            revenue_recognised_at IS NULL OR
            (status IN ('PAID', 'REFUNDED') AND paid_at IS NOT NULL)
        ),
    ADD CONSTRAINT tours_operator_payment_paid_is_recognised
        CHECK (status <> 'PAID' OR revenue_recognised_at IS NOT NULL);

COMMENT ON COLUMN wego.tours_operator_payment.revenue_recognised_at IS
    'Set once when a capture is accepted as revenue (PAID). Never set for REVIEW_REQUIRED captures and never cleared by a refund. Finance recognises revenue and refunds only for rows where this is not null.';
