-- A captured payment may require staff review after revenue recognition, for
-- example when Paymob reports a partial refund that the first-launch payment
-- model deliberately cannot auto-settle. Keep the original recognition
-- timestamp so finance does not silently rewrite a closed period, while the
-- REVIEW_REQUIRED status makes the exception visible and blocks pretending a
-- full refund was completed.

ALTER TABLE wego.tours_operator_payment
    DROP CONSTRAINT tours_operator_payment_recognised_capture;

ALTER TABLE wego.tours_operator_payment
    ADD CONSTRAINT tours_operator_payment_recognised_capture
        CHECK (
            revenue_recognised_at IS NULL OR
            (status IN ('PAID', 'REFUNDED', 'REVIEW_REQUIRED') AND paid_at IS NOT NULL)
        );

COMMENT ON COLUMN wego.tours_operator_payment.revenue_recognised_at IS
    'Set once when a capture is accepted as revenue. Kept through refund or captured-payment review; finance changes only from explicit ledger events.';
