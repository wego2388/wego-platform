-- ============================================================
-- V26 — Tours Operator: emergency sales control
-- ============================================================
--
-- One row at most (id = 1). Lets a manager pause new public bookings and/or
-- new payment checkouts within seconds, without a deploy, while the Paymob
-- webhook, booking expiry and staff operations keep running. No row means
-- sales are open. The reason is a short staff note shown only in the ERP and
-- must not contain customer data.

CREATE TABLE wego.tours_operator_sales_control (
    id                  smallint    PRIMARY KEY,
    bookings_paused     boolean     NOT NULL,
    payments_paused     boolean     NOT NULL,
    reason              varchar(300),
    updated_by_user_id  uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    updated_at          timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_sales_control_singleton CHECK (id = 1)
);
