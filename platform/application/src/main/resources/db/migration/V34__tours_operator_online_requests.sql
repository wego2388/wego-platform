-- Website requests are NOT paid bookings and do not reserve inventory.
-- Existing OFFICE/ONLINE booking semantics, payment ledger and expiry remain unchanged.
CREATE TABLE wego.tours_operator_online_request (
    id uuid PRIMARY KEY,
    reference varchar(40) NOT NULL UNIQUE,
    payload_hash varchar(64) NOT NULL,
    tour_id uuid NOT NULL REFERENCES wego.tours_operator_tour(id),
    preferred_date date NOT NULL,
    preferred_time varchar(16),
    adults_count integer NOT NULL CHECK (adults_count BETWEEN 1 AND 50),
    children_count integer NOT NULL CHECK (children_count BETWEEN 0 AND 50),
    price_option_code varchar(40),
    unit_count integer CHECK (unit_count BETWEEN 1 AND 50),
    estimated_total numeric(12,2) NOT NULL CHECK (estimated_total >= 0),
    full_name varchar(200) NOT NULL,
    phone varchar(32) NOT NULL,
    nationality varchar(2) NOT NULL,
    email varchar(320),
    hotel_name varchar(200) NOT NULL,
    special_requests varchar(2000),
    locale varchar(2) NOT NULL CHECK (locale IN ('en','ar','ru','it')),
    status varchar(16) NOT NULL CHECK (status IN ('NEW','IN_PROGRESS','CONVERTED','CLOSED')),
    revision integer NOT NULL DEFAULT 0 CHECK (revision >= 0),
    booking_id uuid UNIQUE REFERENCES wego.tours_operator_booking(id),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CHECK ((status = 'CONVERTED') = (booking_id IS NOT NULL)),
    CHECK ((price_option_code IS NULL) = (unit_count IS NULL))
);
CREATE INDEX tours_operator_online_request_inbox_idx
    ON wego.tours_operator_online_request(status, created_at DESC, id);

CREATE TABLE wego.tours_operator_online_request_audit (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES wego.tours_operator_online_request(id),
    status varchar(16) NOT NULL CHECK (status IN ('NEW','IN_PROGRESS','CONVERTED','CLOSED')),
    actor_user_id uuid REFERENCES wego.identity_user(id) ON DELETE SET NULL,
    occurred_at timestamp with time zone NOT NULL
);
CREATE INDEX tours_operator_online_request_audit_idx
    ON wego.tours_operator_online_request_audit(request_id, occurred_at, id);

COMMENT ON TABLE wego.tours_operator_online_request IS
    'Guest website requests; staff booking:view reads, booking:create-office follows up/converts. No seat or payment claim. PII is never placed in URLs/outbox payloads.';
