-- Website requests become confirmed OFFICE bookings only after staff checks a
-- real departure, capacity and price. Their email is distinct from the online
-- payment-confirmed email because no payment is captured by this transition.
ALTER TABLE wego.tours_operator_notification
    DROP CONSTRAINT tours_operator_notification_kind_known;

ALTER TABLE wego.tours_operator_notification
    ADD CONSTRAINT tours_operator_notification_kind_known
        CHECK (kind IN (
            'BOOKING_CONFIRMED',
            'OFFICE_REQUEST_CONFIRMED',
            'BOOKING_CANCELLED',
            'REVIEW_REQUEST'
        ));
