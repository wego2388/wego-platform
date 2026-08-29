CREATE TABLE wego.divers_equipment (
    id uuid PRIMARY KEY,
    equipment_type varchar(32) NOT NULL,
    label text NOT NULL,
    -- The code printed on/attached to the physical item. Unique so a
    -- scanned code always resolves to exactly one real item.
    qr_code varchar(128) NOT NULL,
    item_size varchar(16),
    serial_number varchar(64),
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at timestamp with time zone NOT NULL,
    retired_at timestamp with time zone,
    CONSTRAINT divers_equipment_type_known
        CHECK (equipment_type IN ('BCD', 'REGULATOR', 'TANK', 'WETSUIT', 'FIN', 'MASK', 'DIVE_COMPUTER', 'OTHER')),
    CONSTRAINT divers_equipment_label_not_blank
        CHECK (length(trim(label)) > 0),
    CONSTRAINT divers_equipment_qr_code_not_blank
        CHECK (length(trim(qr_code)) > 0),
    CONSTRAINT divers_equipment_qr_code_unique
        UNIQUE (qr_code),
    CONSTRAINT divers_equipment_status_known
        CHECK (status IN ('ACTIVE', 'IN_MAINTENANCE', 'RETIRED')),
    CONSTRAINT divers_equipment_retired_at_matches_status
        CHECK (
            (status = 'RETIRED' AND retired_at IS NOT NULL)
            OR (status != 'RETIRED' AND retired_at IS NULL)
        )
);

CREATE INDEX divers_equipment_active_idx
    ON wego.divers_equipment (status)
    WHERE status != 'RETIRED';

CREATE TABLE wego.divers_equipment_service_record (
    id uuid PRIMARY KEY,
    equipment_id uuid NOT NULL REFERENCES wego.divers_equipment (id) ON DELETE CASCADE,
    serviced_on date NOT NULL,
    description text NOT NULL,
    performed_by text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT divers_equipment_service_record_description_not_blank
        CHECK (length(trim(description)) > 0)
);

CREATE INDEX divers_equipment_service_record_equipment_idx
    ON wego.divers_equipment_service_record (equipment_id, serviced_on DESC);

CREATE TABLE wego.divers_equipment_rental_record (
    id uuid PRIMARY KEY,
    equipment_id uuid NOT NULL REFERENCES wego.divers_equipment (id) ON DELETE CASCADE,
    customer_name text NOT NULL,
    rented_on date NOT NULL,
    -- NULL means still out with the customer — this is what "one open
    -- rental at a time per item" is checked against at the application
    -- layer before a new rental can start.
    returned_on date,
    notes text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT divers_equipment_rental_record_customer_name_not_blank
        CHECK (length(trim(customer_name)) > 0),
    CONSTRAINT divers_equipment_rental_record_returned_after_rented
        CHECK (returned_on IS NULL OR returned_on >= rented_on)
);

CREATE INDEX divers_equipment_rental_record_equipment_idx
    ON wego.divers_equipment_rental_record (equipment_id, rented_on DESC);

-- Backs "does this item already have an open rental" with a real
-- database-level guarantee, not just an application-level check: two
-- concurrent rental-start requests for the same item can't both succeed.
CREATE UNIQUE INDEX divers_equipment_rental_record_one_open_per_item_idx
    ON wego.divers_equipment_rental_record (equipment_id)
    WHERE returned_on IS NULL;

CREATE TABLE wego.divers_equipment_audit_event (
    id uuid PRIMARY KEY,
    equipment_id uuid NOT NULL REFERENCES wego.divers_equipment (id) ON DELETE CASCADE,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(32) NOT NULL,
    actor_user_id uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    from_status varchar(16),
    to_status varchar(16),
    correlation_id uuid,
    CONSTRAINT divers_equipment_audit_event_type_known
        CHECK (event_type IN ('EQUIPMENT_CREATED', 'EQUIPMENT_STATUS_CHANGED'))
);

CREATE INDEX divers_equipment_audit_event_equipment_idx
    ON wego.divers_equipment_audit_event (equipment_id);

COMMENT ON TABLE wego.divers_equipment IS
    'A real, individually QR-coded piece of dive equipment or a tank. Status tracks ACTIVE/IN_MAINTENANCE/RETIRED; RETIRED is terminal.';
COMMENT ON TABLE wego.divers_equipment_service_record IS
    'Append-only maintenance log, one row per real service event on one item.';
COMMENT ON TABLE wego.divers_equipment_rental_record IS
    'Append-only rental history, one row per real rental. NULL returned_on means the item is currently out.';
COMMENT ON TABLE wego.divers_equipment_audit_event IS
    'Append-only record of equipment lifecycle transitions.';

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'equipment:manage'),
    ('platform-admin', 'equipment:view');
