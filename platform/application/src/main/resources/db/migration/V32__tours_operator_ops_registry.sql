-- ============================================================
-- V32 — Tours Operator: suppliers, drivers, vehicles, daily assignment
-- ============================================================
--
-- WEGO-016-OPS2-E. Registries of who and what runs a departure, plus the daily
-- assignment of a driver, an optional vehicle and one or more suppliers to a
-- tour slot (a departure). Nothing is ever deleted: a record that was used goes
-- INACTIVE so history stays readable. Agreed supplier prices, costs and
-- settlements are NOT here (OPS2-F); only which suppliers serve which tours.
--
-- Optimistic locking: every registry row and every assignment carries a
-- `revision` that a writer must echo back; a stale write is refused.

-- ── Suppliers ────────────────────────────────────────────────

CREATE TABLE wego.tours_operator_supplier (
    id                    uuid PRIMARY KEY,
    code                  varchar(24)  NOT NULL,
    name                  varchar(120) NOT NULL,
    service_type          varchar(24)  NOT NULL,
    contact_person        varchar(120),
    business_phone        varchar(32),
    confirmation_channel  varchar(16),
    notice_hours          integer,
    pricing_basis         varchar(24),
    currency              varchar(3),
    settlement_cadence    varchar(24),
    payment_method        varchar(24),
    cancellation_terms    varchar(1000),
    active                boolean NOT NULL DEFAULT true,
    revision              integer NOT NULL DEFAULT 1,
    created_at            timestamp with time zone NOT NULL,
    created_by_user_id    uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    updated_at            timestamp with time zone NOT NULL,
    updated_by_user_id    uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,

    CONSTRAINT tours_operator_supplier_code_shape CHECK (code ~ '^[A-Z][A-Z0-9-]{1,23}$'),
    CONSTRAINT tours_operator_supplier_name_shape CHECK (name = btrim(name) AND length(name) >= 1),
    CONSTRAINT tours_operator_supplier_service_type_known
        CHECK (service_type IN ('DIVING_SNORKELING', 'QUAD_BUGGY_SAFARI', 'BOAT', 'TRANSPORT', 'ATTRACTION', 'OTHER')),
    CONSTRAINT tours_operator_supplier_channel_known
        CHECK (confirmation_channel IS NULL OR confirmation_channel IN ('WHATSAPP', 'PHONE', 'EMAIL', 'OTHER')),
    CONSTRAINT tours_operator_supplier_notice_range CHECK (notice_hours IS NULL OR notice_hours BETWEEN 0 AND 720),
    CONSTRAINT tours_operator_supplier_pricing_known
        CHECK (pricing_basis IS NULL OR pricing_basis IN ('PER_PERSON', 'PER_UNIT', 'PER_TRIP', 'PERCENT_OF_SALE', 'OTHER')),
    CONSTRAINT tours_operator_supplier_currency_known CHECK (currency IS NULL OR currency IN ('EGP', 'EUR')),
    CONSTRAINT tours_operator_supplier_settlement_known
        CHECK (settlement_cadence IS NULL OR settlement_cadence IN ('AFTER_EACH_TRIP', 'WEEKLY', 'MONTHLY', 'OTHER')),
    CONSTRAINT tours_operator_supplier_payment_known
        CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'INSTAPAY', 'MOBILE_WALLET', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT tours_operator_supplier_phone_shape
        CHECK (business_phone IS NULL OR business_phone ~ '^\+?[0-9][0-9 ()-]{5,22}[0-9]$'),
    CONSTRAINT tours_operator_supplier_revision_positive CHECK (revision >= 1)
);

CREATE UNIQUE INDEX tours_operator_supplier_code_unique ON wego.tours_operator_supplier (code);

COMMENT ON TABLE wego.tours_operator_supplier IS
    'Registry of suppliers that run a tour or part of it. Never deleted: a used supplier becomes inactive. Agreed prices live in OPS2-F, not here.';

-- Which suppliers serve which tours (many-to-many); lets assignment suggest them.
CREATE TABLE wego.tours_operator_supplier_tour (
    supplier_id  uuid NOT NULL REFERENCES wego.tours_operator_supplier (id) ON DELETE RESTRICT,
    tour_id      uuid NOT NULL REFERENCES wego.tours_operator_tour (id) ON DELETE CASCADE,
    PRIMARY KEY (supplier_id, tour_id)
);

CREATE INDEX tours_operator_supplier_tour_tour_idx ON wego.tours_operator_supplier_tour (tour_id);

-- ── Drivers ──────────────────────────────────────────────────

CREATE TABLE wego.tours_operator_driver (
    id                    uuid PRIMARY KEY,
    name                  varchar(120) NOT NULL,
    work_phone            varchar(32),
    engagement_type       varchar(16)  NOT NULL,
    licence_valid_until   date         NOT NULL,
    active                boolean NOT NULL DEFAULT true,
    revision              integer NOT NULL DEFAULT 1,
    created_at            timestamp with time zone NOT NULL,
    created_by_user_id    uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    updated_at            timestamp with time zone NOT NULL,
    updated_by_user_id    uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,

    CONSTRAINT tours_operator_driver_name_shape CHECK (name = btrim(name) AND length(name) >= 1),
    CONSTRAINT tours_operator_driver_engagement_known CHECK (engagement_type IN ('PER_TRIP', 'MONTHLY', 'DAILY', 'OTHER')),
    CONSTRAINT tours_operator_driver_phone_shape
        CHECK (work_phone IS NULL OR work_phone ~ '^\+?[0-9][0-9 ()-]{5,22}[0-9]$'),
    CONSTRAINT tours_operator_driver_revision_positive CHECK (revision >= 1)
);

COMMENT ON TABLE wego.tours_operator_driver IS
    'Drivers who take guests to the departure. Name, work phone and licence expiry only: no ID number or other personal data. Never deleted.';

-- ── Vehicles ─────────────────────────────────────────────────

CREATE TABLE wego.tours_operator_vehicle (
    id                    uuid PRIMARY KEY,
    label                 varchar(80),
    plate                 varchar(24),
    vehicle_type          varchar(16) NOT NULL,
    seats                 integer NOT NULL,
    ownership             varchar(8)  NOT NULL,
    hired_from_supplier_id uuid REFERENCES wego.tours_operator_supplier (id) ON DELETE RESTRICT,
    active                boolean NOT NULL DEFAULT true,
    revision              integer NOT NULL DEFAULT 1,
    created_at            timestamp with time zone NOT NULL,
    created_by_user_id    uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    updated_at            timestamp with time zone NOT NULL,
    updated_by_user_id    uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,

    CONSTRAINT tours_operator_vehicle_identified CHECK (label IS NOT NULL OR plate IS NOT NULL),
    CONSTRAINT tours_operator_vehicle_label_shape CHECK (label IS NULL OR (label = btrim(label) AND length(label) >= 1)),
    CONSTRAINT tours_operator_vehicle_plate_shape CHECK (plate IS NULL OR (plate = btrim(plate) AND length(plate) >= 1)),
    CONSTRAINT tours_operator_vehicle_type_known
        CHECK (vehicle_type IN ('SEDAN', 'SUV', 'JEEP', 'VAN', 'MINIBUS', 'BUS', 'OTHER')),
    CONSTRAINT tours_operator_vehicle_seats_range CHECK (seats BETWEEN 1 AND 80),
    CONSTRAINT tours_operator_vehicle_ownership_known CHECK (ownership IN ('OWNED', 'HIRED')),
    CONSTRAINT tours_operator_vehicle_hired_from_only_when_hired
        CHECK (hired_from_supplier_id IS NULL OR ownership = 'HIRED'),
    CONSTRAINT tours_operator_vehicle_revision_positive CHECK (revision >= 1)
);

-- [jooq ignore start]
CREATE UNIQUE INDEX tours_operator_vehicle_plate_unique
    ON wego.tours_operator_vehicle (upper(plate)) WHERE plate IS NOT NULL;
-- [jooq ignore stop]

COMMENT ON TABLE wego.tours_operator_vehicle IS
    'Vehicles used for pickups and trips (owned or hired). May stay empty: the owner has not supplied vehicles yet. Never deleted.';

-- ── Daily assignment ─────────────────────────────────────────

-- The assignment rows copy the slot's day and window so that "same driver, same
-- departure window" is enforced by the database itself; the composite key keeps
-- the copy honest.
ALTER TABLE wego.tours_operator_tour_slot
    ADD CONSTRAINT tours_operator_slot_id_day_window_unique UNIQUE (id, date, time_slot);

CREATE TABLE wego.tours_operator_slot_assignment (
    slot_id              uuid PRIMARY KEY,
    service_date         date        NOT NULL,
    time_slot            varchar(16) NOT NULL,
    driver_id            uuid REFERENCES wego.tours_operator_driver (id) ON DELETE RESTRICT,
    vehicle_id           uuid REFERENCES wego.tours_operator_vehicle (id) ON DELETE RESTRICT,
    -- Staff-written note printed on the supplier orders of this departure. Customers'
    -- own special requests are never forwarded to suppliers (they can carry phones or
    -- health details); staff copy only what the supplier needs.
    supplier_note        varchar(500),
    revision             integer     NOT NULL DEFAULT 1,
    assigned_at          timestamp with time zone NOT NULL,
    assigned_by_user_id  uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    updated_at           timestamp with time zone NOT NULL,
    updated_by_user_id   uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,

    CONSTRAINT tours_operator_slot_assignment_slot_fk
        FOREIGN KEY (slot_id, service_date, time_slot)
        REFERENCES wego.tours_operator_tour_slot (id, date, time_slot) ON DELETE CASCADE,
    CONSTRAINT tours_operator_slot_assignment_supplier_note_shape
        CHECK (supplier_note IS NULL OR (supplier_note = btrim(supplier_note) AND length(supplier_note) >= 1)),
    CONSTRAINT tours_operator_slot_assignment_revision_positive CHECK (revision >= 1)
);

-- One driver / one vehicle cannot serve two departures of the same day and window.
CREATE UNIQUE INDEX tours_operator_slot_assignment_driver_window_unique
    ON wego.tours_operator_slot_assignment (driver_id, service_date, time_slot) WHERE driver_id IS NOT NULL;
CREATE UNIQUE INDEX tours_operator_slot_assignment_vehicle_window_unique
    ON wego.tours_operator_slot_assignment (vehicle_id, service_date, time_slot) WHERE vehicle_id IS NOT NULL;
CREATE INDEX tours_operator_slot_assignment_day_idx ON wego.tours_operator_slot_assignment (service_date);

CREATE TABLE wego.tours_operator_slot_assignment_supplier (
    slot_id      uuid NOT NULL REFERENCES wego.tours_operator_slot_assignment (slot_id) ON DELETE CASCADE,
    supplier_id  uuid NOT NULL REFERENCES wego.tours_operator_supplier (id) ON DELETE RESTRICT,
    PRIMARY KEY (slot_id, supplier_id)
);

COMMENT ON TABLE wego.tours_operator_slot_assignment IS
    'Driver and optional vehicle of one departure (tour slot) plus, in the child table, its suppliers. Revision is the optimistic lock.';

-- Append-only record of who assigned, changed or cleared a departure's assignment.
CREATE TABLE wego.tours_operator_assignment_audit (
    id              uuid PRIMARY KEY,
    slot_id         uuid NOT NULL,
    service_date    date NOT NULL,
    time_slot       varchar(16) NOT NULL,
    action          varchar(16) NOT NULL,
    revision        integer NOT NULL,
    driver_id       uuid,
    vehicle_id      uuid,
    supplier_ids    text NOT NULL DEFAULT '',
    supplier_note   varchar(500),
    actor_user_id   uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    occurred_at     timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_assignment_audit_action_known CHECK (action IN ('ASSIGNED', 'CHANGED', 'CLEARED'))
);

CREATE INDEX tours_operator_assignment_audit_slot_idx ON wego.tours_operator_assignment_audit (slot_id, occurred_at);

-- The guard is PostgreSQL-only (PL/pgSQL), so jOOQ's DDL parser skips it.
-- [jooq ignore start]
-- Append-only: no edits, deletes or TRUNCATE. The only permitted change is the database
-- detaching a deleted staff user (ON DELETE SET NULL runs as a nested trigger, depth > 1).
CREATE FUNCTION wego.tours_operator_assignment_audit_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND pg_trigger_depth() > 1
       AND NEW.actor_user_id IS NULL
       AND (to_jsonb(NEW) - 'actor_user_id') = (to_jsonb(OLD) - 'actor_user_id') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'tours_operator_assignment_audit is append-only';
END;
$$;

CREATE TRIGGER tours_operator_assignment_audit_append_only
    BEFORE UPDATE OR DELETE ON wego.tours_operator_assignment_audit
    FOR EACH ROW EXECUTE FUNCTION wego.tours_operator_assignment_audit_guard();

CREATE TRIGGER tours_operator_assignment_audit_no_truncate
    BEFORE TRUNCATE ON wego.tours_operator_assignment_audit
    FOR EACH STATEMENT EXECUTE FUNCTION wego.tours_operator_assignment_audit_guard();
-- [jooq ignore stop]

-- ── Documents: driver sheet and supplier order ───────────────

-- [jooq ignore start]
ALTER TABLE wego.tours_operator_document_print DROP CONSTRAINT tours_operator_document_print_type_known;
ALTER TABLE wego.tours_operator_document_print
    ADD CONSTRAINT tours_operator_document_print_type_known
        CHECK (document_type IN ('VOUCHER', 'RECEIPT', 'RUN_SHEET', 'PICKUP_MANIFEST', 'CANCELLATION_FORM', 'DRIVER_SHEET', 'SUPPLIER_ORDER'));
-- A supplier order's subject is "<slot id>:<supplier id>" (73 characters).
ALTER TABLE wego.tours_operator_document_print ALTER COLUMN subject_key TYPE varchar(96);
-- [jooq ignore stop]

-- ── Permissions ──────────────────────────────────────────────

INSERT INTO wego.identity_permission (code, description) VALUES
    ('tours-operator.supplier:manage', 'View and manage the supplier registry (carries supplier contact phones) and which tours each supplier serves'),
    ('tours-operator.fleet:manage', 'View and manage drivers (carries work phones and licence expiry) and vehicles'),
    ('tours-operator.assignment:manage', 'Assign drivers, vehicles and suppliers to a day''s departures and see the day''s assignments (names only, no phones)');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'tours-operator.supplier:manage'),
    ('platform-admin', 'tours-operator.fleet:manage'),
    ('platform-admin', 'tours-operator.assignment:manage');
