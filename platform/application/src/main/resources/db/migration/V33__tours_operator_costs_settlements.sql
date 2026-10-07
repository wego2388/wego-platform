-- ============================================================
-- V33 — Tours Operator: costs, payables, settlements, cash box, office refunds
-- ============================================================
--
-- WEGO-016-OPS2-F. Everything here that records money is append-only: a
-- mistake is cancelled by a REVERSAL row that names what it reverses and why,
-- never edited or deleted (database triggers refuse UPDATE, DELETE and
-- TRUNCATE; the only allowed change is the database itself detaching a deleted
-- staff user through ON DELETE SET NULL). Paymob online payments are not
-- touched by anything in this migration, and supplier/driver money never
-- mixes with customer money: each has its own ledger.
--
-- Amounts are numeric(12,2) in the currency of the row (EGP or EUR). EGP is
-- converted to EUR only in reports, with the manager-set daily rate
-- (tours_operator_fx_rate) and the report says which rate it used.

-- ── Cost components ──────────────────────────────────────────
-- What one departure of a tour costs us, effective-dated. A component belongs
-- to a tour (supplier price, extra own costs such as entry fees, food, water,
-- or a fixed amount per departure) or to a driver (pay per trip). Rows are
-- never edited: a change ends the old row (valid_until, ended_at/by — the only
-- permitted update, once) and adds a new one that names the row it replaces.

CREATE TABLE wego.tours_operator_cost_component (
    id                      uuid PRIMARY KEY,
    tour_id                 uuid REFERENCES wego.tours_operator_tour (id) ON DELETE RESTRICT,
    driver_id               uuid REFERENCES wego.tours_operator_driver (id) ON DELETE RESTRICT,
    category                varchar(16) NOT NULL,
    label                   varchar(80) NOT NULL,
    basis                   varchar(16) NOT NULL,
    currency                varchar(3)  NOT NULL,
    amount                  numeric(12, 2) NOT NULL,
    -- PER_PERSON only; null = a child costs the adult amount.
    child_amount            numeric(12, 2),
    -- SUPPLIER only; null = owed to whichever single supplier is assigned to the departure.
    supplier_id             uuid REFERENCES wego.tours_operator_supplier (id) ON DELETE RESTRICT,
    valid_from              date NOT NULL,
    -- Inclusive. valid_from - 1 means the row never took effect.
    valid_until             date,
    replaces_component_id   uuid REFERENCES wego.tours_operator_cost_component (id) ON DELETE RESTRICT,
    note                    varchar(500),
    -- Idempotency key of the staff request (or of the import row) that created it.
    client_request_id       uuid NOT NULL,
    created_by_user_id      uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    created_at              timestamp with time zone NOT NULL,
    ended_by_user_id        uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    ended_at                timestamp with time zone,

    CONSTRAINT tours_operator_cost_component_owner
        CHECK ((tour_id IS NULL) <> (driver_id IS NULL)),
    CONSTRAINT tours_operator_cost_component_category_known
        CHECK (category IN ('SUPPLIER', 'OWN_EXTRA', 'FIXED', 'DRIVER')),
    CONSTRAINT tours_operator_cost_component_driver_category
        CHECK ((category = 'DRIVER') = (driver_id IS NOT NULL)),
    CONSTRAINT tours_operator_cost_component_basis_known
        CHECK (basis IN ('PER_PERSON', 'PER_UNIT', 'PER_DEPARTURE')),
    CONSTRAINT tours_operator_cost_component_per_departure_categories
        CHECK (category NOT IN ('FIXED', 'DRIVER') OR basis = 'PER_DEPARTURE'),
    CONSTRAINT tours_operator_cost_component_currency_known CHECK (currency IN ('EGP', 'EUR')),
    CONSTRAINT tours_operator_cost_component_amount_range CHECK (amount >= 0 AND amount <= 9999999.99),
    CONSTRAINT tours_operator_cost_component_child_amount
        CHECK (child_amount IS NULL OR (basis = 'PER_PERSON' AND child_amount >= 0 AND child_amount <= 9999999.99)),
    CONSTRAINT tours_operator_cost_component_supplier_only
        CHECK (supplier_id IS NULL OR category = 'SUPPLIER'),
    CONSTRAINT tours_operator_cost_component_label_shape
        CHECK (label = btrim(label) AND length(label) BETWEEN 1 AND 80 AND label !~ '[[:cntrl:]]'),
    CONSTRAINT tours_operator_cost_component_note_shape
        CHECK (note IS NULL OR (note = btrim(note) AND length(note) >= 1)),
    CONSTRAINT tours_operator_cost_component_validity
        CHECK (valid_until IS NULL OR valid_until >= valid_from - 1),
    CONSTRAINT tours_operator_cost_component_ended_shape
        CHECK (ended_by_user_id IS NULL OR ended_at IS NOT NULL)
);

CREATE INDEX tours_operator_cost_component_tour_idx ON wego.tours_operator_cost_component (tour_id, valid_from);
CREATE INDEX tours_operator_cost_component_driver_idx ON wego.tours_operator_cost_component (driver_id, valid_from);
CREATE UNIQUE INDEX tours_operator_cost_component_request_unique
    ON wego.tours_operator_cost_component (created_by_user_id, client_request_id);
-- A row is replaced at most once.
CREATE UNIQUE INDEX tours_operator_cost_component_replaces_unique
    ON wego.tours_operator_cost_component (replaces_component_id) WHERE replaces_component_id IS NOT NULL;

COMMENT ON TABLE wego.tours_operator_cost_component IS
    'Effective-dated cost of a tour departure (per person, per unit or per departure) or of a driver trip, in EGP or EUR. Never edited: the only change is ending a row once; a new value is a new row.';

-- ── Office refunds (money returned on cancelled office bookings) ──────
-- Separate from the office collections ledger (V30) and from Paymob refunds:
-- a refund never changes what was collected, it records what was handed back.

CREATE TABLE wego.tours_operator_office_refund (
    id                   uuid PRIMARY KEY,
    booking_id           uuid NOT NULL REFERENCES wego.tours_operator_booking (id) ON DELETE RESTRICT,
    kind                 varchar(16) NOT NULL,
    method               varchar(24) NOT NULL,
    currency_paid        varchar(3)  NOT NULL,
    amount_paid          numeric(12, 2) NOT NULL,
    amount_eur           numeric(10, 2) NOT NULL,
    fx_rate              numeric(12, 4),
    fx_rate_id           uuid REFERENCES wego.tours_operator_fx_rate (id) ON DELETE RESTRICT,
    reference            varchar(64),
    reason               varchar(500) NOT NULL,
    reverses_refund_id   uuid REFERENCES wego.tours_operator_office_refund (id) ON DELETE RESTRICT,
    recorded_by_user_id  uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    client_request_id    uuid NOT NULL,
    recorded_at          timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_office_refund_kind_known CHECK (kind IN ('REFUND', 'REVERSAL')),
    CONSTRAINT tours_operator_office_refund_method_known
        CHECK (method IN ('CASH', 'MOBILE_WALLET', 'CARD_TERMINAL', 'INSTAPAY', 'FAWRY_OFFICE', 'BANK_TRANSFER')),
    CONSTRAINT tours_operator_office_refund_currency_known CHECK (currency_paid IN ('EUR', 'EGP')),
    CONSTRAINT tours_operator_office_refund_amount_positive CHECK (amount_paid > 0 AND amount_eur > 0),
    CONSTRAINT tours_operator_office_refund_rate_matches_currency
        CHECK (
            (currency_paid = 'EUR' AND fx_rate IS NULL AND fx_rate_id IS NULL AND amount_paid = amount_eur)
            OR (currency_paid = 'EGP' AND fx_rate IS NOT NULL AND fx_rate > 0 AND fx_rate_id IS NOT NULL)
        ),
    CONSTRAINT tours_operator_office_refund_reference_shape
        CHECK (reference IS NULL OR (reference = btrim(reference) AND length(reference) BETWEEN 1 AND 64 AND reference !~ '[[:cntrl:]]')),
    CONSTRAINT tours_operator_office_refund_reference_required
        CHECK (
            (kind = 'REFUND' AND method = 'CASH' AND reference IS NULL)
            OR (kind = 'REFUND' AND method <> 'CASH' AND reference IS NOT NULL)
            OR (kind = 'REVERSAL' AND reference IS NULL)
        ),
    CONSTRAINT tours_operator_office_refund_reason_shape
        CHECK (reason = btrim(reason) AND length(reason) >= 1),
    CONSTRAINT tours_operator_office_refund_reversal_shape
        CHECK ((kind = 'REVERSAL') = (reverses_refund_id IS NOT NULL))
);

CREATE UNIQUE INDEX tours_operator_office_refund_reversal_unique
    ON wego.tours_operator_office_refund (reverses_refund_id) WHERE reverses_refund_id IS NOT NULL;
CREATE UNIQUE INDEX tours_operator_office_refund_request_unique
    ON wego.tours_operator_office_refund (recorded_by_user_id, client_request_id);
CREATE INDEX tours_operator_office_refund_booking_idx ON wego.tours_operator_office_refund (booking_id, recorded_at);
CREATE INDEX tours_operator_office_refund_recorded_idx ON wego.tours_operator_office_refund (recorded_at);

COMMENT ON TABLE wego.tours_operator_office_refund IS
    'Append-only record of money handed back to customers of cancelled office bookings (manager permission). Never a Paymob refund; corrections are REVERSAL rows.';

-- ── Payables: manual adjustments ─────────────────────────────
-- Amounts owed to a supplier or driver are derived from departures that ran
-- (cost components x guests; driver trip rate). What the derivation cannot
-- know is entered here: a CHARGE adds to what is owed (for example a driver
-- with no fixed trip rate), a DEDUCTION reduces it (a documented penalty or
-- discount). Never edited: cancelled by a REVERSAL.

CREATE TABLE wego.tours_operator_payable_adjustment (
    id                      uuid PRIMARY KEY,
    party_type              varchar(8) NOT NULL,
    supplier_id             uuid REFERENCES wego.tours_operator_supplier (id) ON DELETE RESTRICT,
    driver_id               uuid REFERENCES wego.tours_operator_driver (id) ON DELETE RESTRICT,
    slot_id                 uuid REFERENCES wego.tours_operator_tour_slot (id) ON DELETE RESTRICT,
    service_date            date NOT NULL,
    kind                    varchar(16) NOT NULL,
    currency                varchar(3) NOT NULL,
    amount                  numeric(12, 2) NOT NULL,
    reason                  varchar(500) NOT NULL,
    reverses_adjustment_id  uuid REFERENCES wego.tours_operator_payable_adjustment (id) ON DELETE RESTRICT,
    recorded_by_user_id     uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    client_request_id       uuid NOT NULL,
    recorded_at             timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_payable_adjustment_party
        CHECK ((party_type = 'SUPPLIER' AND supplier_id IS NOT NULL AND driver_id IS NULL)
            OR (party_type = 'DRIVER' AND driver_id IS NOT NULL AND supplier_id IS NULL)),
    CONSTRAINT tours_operator_payable_adjustment_kind_known CHECK (kind IN ('CHARGE', 'DEDUCTION', 'REVERSAL')),
    CONSTRAINT tours_operator_payable_adjustment_currency_known CHECK (currency IN ('EGP', 'EUR')),
    CONSTRAINT tours_operator_payable_adjustment_amount_range CHECK (amount > 0 AND amount <= 9999999.99),
    CONSTRAINT tours_operator_payable_adjustment_reason_shape CHECK (reason = btrim(reason) AND length(reason) >= 1),
    CONSTRAINT tours_operator_payable_adjustment_reversal_shape
        CHECK ((kind = 'REVERSAL') = (reverses_adjustment_id IS NOT NULL))
);

CREATE UNIQUE INDEX tours_operator_payable_adjustment_reversal_unique
    ON wego.tours_operator_payable_adjustment (reverses_adjustment_id) WHERE reverses_adjustment_id IS NOT NULL;
CREATE UNIQUE INDEX tours_operator_payable_adjustment_request_unique
    ON wego.tours_operator_payable_adjustment (recorded_by_user_id, client_request_id);
CREATE INDEX tours_operator_payable_adjustment_supplier_idx ON wego.tours_operator_payable_adjustment (supplier_id, service_date);
CREATE INDEX tours_operator_payable_adjustment_driver_idx ON wego.tours_operator_payable_adjustment (driver_id, service_date);
CREATE INDEX tours_operator_payable_adjustment_slot_idx ON wego.tours_operator_payable_adjustment (slot_id);

-- ── Settlement approvals (owner) ─────────────────────────────
-- A payment above the manager's limit (5000 EGP per payment, owner-delegated
-- default) needs an approval first: one approval authorises exactly one
-- payment of that party, currency and amount, and is consumed by it.

CREATE TABLE wego.tours_operator_settlement_approval (
    id                   uuid PRIMARY KEY,
    party_type           varchar(8) NOT NULL,
    supplier_id          uuid REFERENCES wego.tours_operator_supplier (id) ON DELETE RESTRICT,
    driver_id            uuid REFERENCES wego.tours_operator_driver (id) ON DELETE RESTRICT,
    currency             varchar(3) NOT NULL,
    amount               numeric(12, 2) NOT NULL,
    note                 varchar(500),
    approved_by_user_id  uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    client_request_id    uuid NOT NULL,
    approved_at          timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_settlement_approval_party
        CHECK ((party_type = 'SUPPLIER' AND supplier_id IS NOT NULL AND driver_id IS NULL)
            OR (party_type = 'DRIVER' AND driver_id IS NOT NULL AND supplier_id IS NULL)),
    CONSTRAINT tours_operator_settlement_approval_currency_known CHECK (currency IN ('EGP', 'EUR')),
    CONSTRAINT tours_operator_settlement_approval_amount_range CHECK (amount > 0 AND amount <= 9999999.99),
    CONSTRAINT tours_operator_settlement_approval_note_shape CHECK (note IS NULL OR (note = btrim(note) AND length(note) >= 1))
);

CREATE UNIQUE INDEX tours_operator_settlement_approval_request_unique
    ON wego.tours_operator_settlement_approval (approved_by_user_id, client_request_id);

-- ── Settlement payments ──────────────────────────────────────

CREATE TABLE wego.tours_operator_settlement_payment (
    id                     uuid PRIMARY KEY,
    party_type             varchar(8) NOT NULL,
    supplier_id            uuid REFERENCES wego.tours_operator_supplier (id) ON DELETE RESTRICT,
    driver_id              uuid REFERENCES wego.tours_operator_driver (id) ON DELETE RESTRICT,
    kind                   varchar(16) NOT NULL,
    method                 varchar(16) NOT NULL,
    currency               varchar(3) NOT NULL,
    amount                 numeric(12, 2) NOT NULL,
    -- What the payment is worth in EGP for the approval limit (EUR at today's manager rate); null when no rate was set.
    egp_equivalent         numeric(14, 2),
    fx_rate_id             uuid REFERENCES wego.tours_operator_fx_rate (id) ON DELETE RESTRICT,
    reference              varchar(64),
    note                   varchar(500),
    approval_id            uuid REFERENCES wego.tours_operator_settlement_approval (id) ON DELETE RESTRICT,
    reverses_payment_id    uuid REFERENCES wego.tours_operator_settlement_payment (id) ON DELETE RESTRICT,
    reason                 varchar(500),
    recorded_by_user_id    uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    client_request_id      uuid NOT NULL,
    recorded_at            timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_settlement_payment_party
        CHECK ((party_type = 'SUPPLIER' AND supplier_id IS NOT NULL AND driver_id IS NULL)
            OR (party_type = 'DRIVER' AND driver_id IS NOT NULL AND supplier_id IS NULL)),
    CONSTRAINT tours_operator_settlement_payment_kind_known CHECK (kind IN ('PAYMENT', 'REVERSAL')),
    CONSTRAINT tours_operator_settlement_payment_method_known
        CHECK (method IN ('CASH', 'INSTAPAY', 'MOBILE_WALLET', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT tours_operator_settlement_payment_currency_known CHECK (currency IN ('EGP', 'EUR')),
    CONSTRAINT tours_operator_settlement_payment_amount_range CHECK (amount > 0 AND amount <= 9999999.99),
    CONSTRAINT tours_operator_settlement_payment_egp_shape
        CHECK (currency = 'EUR' OR egp_equivalent = amount),
    CONSTRAINT tours_operator_settlement_payment_reference_shape
        CHECK (reference IS NULL OR (reference = btrim(reference) AND length(reference) BETWEEN 1 AND 64 AND reference !~ '[[:cntrl:]]')),
    CONSTRAINT tours_operator_settlement_payment_reference_required
        CHECK (kind = 'REVERSAL' OR method = 'CASH' OR reference IS NOT NULL),
    CONSTRAINT tours_operator_settlement_payment_note_shape CHECK (note IS NULL OR (note = btrim(note) AND length(note) >= 1)),
    CONSTRAINT tours_operator_settlement_payment_reversal_shape
        CHECK (
            (kind = 'PAYMENT' AND reverses_payment_id IS NULL AND reason IS NULL)
            OR (kind = 'REVERSAL' AND reverses_payment_id IS NOT NULL AND approval_id IS NULL
                AND reason IS NOT NULL AND reason = btrim(reason) AND length(reason) >= 1)
        ),
    -- Above the manager limit a payment carries the approval it consumed (no EGP value = above the limit).
    CONSTRAINT tours_operator_settlement_payment_limit
        CHECK (kind = 'REVERSAL' OR approval_id IS NOT NULL OR (egp_equivalent IS NOT NULL AND egp_equivalent <= 5000.00))
);

-- An approval pays exactly once; a payment is reversed at most once.
CREATE UNIQUE INDEX tours_operator_settlement_payment_approval_unique
    ON wego.tours_operator_settlement_payment (approval_id) WHERE approval_id IS NOT NULL;
CREATE UNIQUE INDEX tours_operator_settlement_payment_reversal_unique
    ON wego.tours_operator_settlement_payment (reverses_payment_id) WHERE reverses_payment_id IS NOT NULL;
CREATE UNIQUE INDEX tours_operator_settlement_payment_request_unique
    ON wego.tours_operator_settlement_payment (recorded_by_user_id, client_request_id);
CREATE INDEX tours_operator_settlement_payment_supplier_idx ON wego.tours_operator_settlement_payment (supplier_id, recorded_at);
CREATE INDEX tours_operator_settlement_payment_driver_idx ON wego.tours_operator_settlement_payment (driver_id, recorded_at);
CREATE INDEX tours_operator_settlement_payment_recorded_idx ON wego.tours_operator_settlement_payment (recorded_at);

COMMENT ON TABLE wego.tours_operator_settlement_payment IS
    'Append-only payments to suppliers and drivers. Up to 5000 EGP (per payment) a manager pays directly; above that the payment consumes one owner approval. Corrections are REVERSAL rows.';

-- ── Daily cash box ───────────────────────────────────────────
-- One office cash box per currency. Expected for a day = cash collected
-- (cash at office + cash on pickup, net of reversals) - cash refunds - cash
-- settlement payments recorded that Cairo day. Reception counts (COUNT), a
-- different person with the manager permission confirms (CONFIRM): the day is
-- then closed and no cash entry can be recorded on it. A manager may REOPEN
-- with a reason. The state is the latest event; events are append-only.

CREATE TABLE wego.tours_operator_cash_box_event (
    id               uuid PRIMARY KEY,
    business_date    date NOT NULL,
    currency         varchar(3) NOT NULL,
    sequence         integer NOT NULL,
    kind             varchar(16) NOT NULL,
    expected         numeric(14, 2),
    counted          numeric(14, 2),
    difference       numeric(14, 2),
    note             varchar(500),
    count_event_id   uuid REFERENCES wego.tours_operator_cash_box_event (id) ON DELETE RESTRICT,
    actor_user_id    uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    occurred_at      timestamp with time zone NOT NULL,

    CONSTRAINT tours_operator_cash_box_event_currency_known CHECK (currency IN ('EGP', 'EUR')),
    CONSTRAINT tours_operator_cash_box_event_kind_known CHECK (kind IN ('COUNT', 'CONFIRM', 'REOPEN')),
    CONSTRAINT tours_operator_cash_box_event_sequence_positive CHECK (sequence >= 1),
    CONSTRAINT tours_operator_cash_box_event_shape
        CHECK (
            (kind = 'COUNT' AND expected IS NOT NULL AND counted IS NOT NULL AND counted >= 0
                AND difference = counted - expected AND count_event_id IS NULL)
            OR (kind = 'CONFIRM' AND expected IS NOT NULL AND counted IS NULL AND difference IS NULL AND count_event_id IS NOT NULL)
            OR (kind = 'REOPEN' AND expected IS NULL AND counted IS NULL AND difference IS NULL AND count_event_id IS NULL
                AND note IS NOT NULL)
        ),
    CONSTRAINT tours_operator_cash_box_event_note_shape CHECK (note IS NULL OR (note = btrim(note) AND length(note) >= 1)),
    CONSTRAINT tours_operator_cash_box_event_order_unique UNIQUE (business_date, currency, sequence)
);

COMMENT ON TABLE wego.tours_operator_cash_box_event IS
    'Append-only daily cash-box events per currency: COUNT (reception), CONFIRM (manager; closes the day), REOPEN (manager, with reason). The latest event is the day''s state.';

-- ── Append-only guards ───────────────────────────────────────
-- [jooq ignore start]
CREATE FUNCTION wego.tours_operator_finance_append_only_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    user_columns text[] := TG_ARGV;
    old_rest jsonb;
    new_rest jsonb;
    col text;
BEGIN
    IF TG_OP = 'UPDATE' AND pg_trigger_depth() > 1 THEN
        -- Only the database detaching a deleted staff user (ON DELETE SET NULL) may touch a row.
        old_rest := to_jsonb(OLD);
        new_rest := to_jsonb(NEW);
        FOREACH col IN ARRAY user_columns LOOP
            IF new_rest ->> col IS NOT NULL AND new_rest -> col IS DISTINCT FROM old_rest -> col THEN
                RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
            END IF;
            old_rest := old_rest - col;
            new_rest := new_rest - col;
        END LOOP;
        IF old_rest = new_rest THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER tours_operator_office_refund_append_only
    BEFORE UPDATE OR DELETE ON wego.tours_operator_office_refund
    FOR EACH ROW EXECUTE FUNCTION wego.tours_operator_finance_append_only_guard('recorded_by_user_id');
CREATE TRIGGER tours_operator_office_refund_no_truncate
    BEFORE TRUNCATE ON wego.tours_operator_office_refund
    FOR EACH STATEMENT EXECUTE FUNCTION wego.tours_operator_finance_append_only_guard();

CREATE TRIGGER tours_operator_payable_adjustment_append_only
    BEFORE UPDATE OR DELETE ON wego.tours_operator_payable_adjustment
    FOR EACH ROW EXECUTE FUNCTION wego.tours_operator_finance_append_only_guard('recorded_by_user_id');
CREATE TRIGGER tours_operator_payable_adjustment_no_truncate
    BEFORE TRUNCATE ON wego.tours_operator_payable_adjustment
    FOR EACH STATEMENT EXECUTE FUNCTION wego.tours_operator_finance_append_only_guard();

CREATE TRIGGER tours_operator_settlement_approval_append_only
    BEFORE UPDATE OR DELETE ON wego.tours_operator_settlement_approval
    FOR EACH ROW EXECUTE FUNCTION wego.tours_operator_finance_append_only_guard('approved_by_user_id');
CREATE TRIGGER tours_operator_settlement_approval_no_truncate
    BEFORE TRUNCATE ON wego.tours_operator_settlement_approval
    FOR EACH STATEMENT EXECUTE FUNCTION wego.tours_operator_finance_append_only_guard();

CREATE TRIGGER tours_operator_settlement_payment_append_only
    BEFORE UPDATE OR DELETE ON wego.tours_operator_settlement_payment
    FOR EACH ROW EXECUTE FUNCTION wego.tours_operator_finance_append_only_guard('recorded_by_user_id');
CREATE TRIGGER tours_operator_settlement_payment_no_truncate
    BEFORE TRUNCATE ON wego.tours_operator_settlement_payment
    FOR EACH STATEMENT EXECUTE FUNCTION wego.tours_operator_finance_append_only_guard();

CREATE TRIGGER tours_operator_cash_box_event_append_only
    BEFORE UPDATE OR DELETE ON wego.tours_operator_cash_box_event
    FOR EACH ROW EXECUTE FUNCTION wego.tours_operator_finance_append_only_guard('actor_user_id');
CREATE TRIGGER tours_operator_cash_box_event_no_truncate
    BEFORE TRUNCATE ON wego.tours_operator_cash_box_event
    FOR EACH STATEMENT EXECUTE FUNCTION wego.tours_operator_finance_append_only_guard();

-- Cost components: besides the user detach, the only change is ending an open row once
-- (valid_until set or shortened, ended_at/ended_by set); everything else is frozen.
CREATE FUNCTION wego.tours_operator_cost_component_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    frozen text[] := ARRAY['valid_until', 'ended_at', 'ended_by_user_id', 'created_by_user_id'];
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF pg_trigger_depth() > 1
           AND (to_jsonb(NEW) - 'created_by_user_id' - 'ended_by_user_id') = (to_jsonb(OLD) - 'created_by_user_id' - 'ended_by_user_id')
           AND (NEW.created_by_user_id IS NULL OR NEW.created_by_user_id = OLD.created_by_user_id)
           AND (NEW.ended_by_user_id IS NULL OR NEW.ended_by_user_id = OLD.ended_by_user_id) THEN
            RETURN NEW;
        END IF;
        IF OLD.ended_at IS NULL
           AND NEW.ended_at IS NOT NULL
           AND NEW.valid_until IS NOT NULL
           AND (OLD.valid_until IS NULL OR NEW.valid_until <= OLD.valid_until)
           AND NEW.created_by_user_id IS NOT DISTINCT FROM OLD.created_by_user_id
           AND (to_jsonb(NEW) - frozen) = (to_jsonb(OLD) - frozen) THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'tours_operator_cost_component rows are never edited; only ended once';
END;
$$;

CREATE TRIGGER tours_operator_cost_component_guarded
    BEFORE UPDATE OR DELETE ON wego.tours_operator_cost_component
    FOR EACH ROW EXECUTE FUNCTION wego.tours_operator_cost_component_guard();
CREATE TRIGGER tours_operator_cost_component_no_truncate
    BEFORE TRUNCATE ON wego.tours_operator_cost_component
    FOR EACH STATEMENT EXECUTE FUNCTION wego.tours_operator_cost_component_guard();
-- [jooq ignore stop]

-- ── Documents: settlement statement ──────────────────────────
-- [jooq ignore start]
ALTER TABLE wego.tours_operator_document_print DROP CONSTRAINT tours_operator_document_print_type_known;
ALTER TABLE wego.tours_operator_document_print
    ADD CONSTRAINT tours_operator_document_print_type_known
        CHECK (document_type IN ('VOUCHER', 'RECEIPT', 'RUN_SHEET', 'PICKUP_MANIFEST', 'CANCELLATION_FORM', 'DRIVER_SHEET',
                                 'SUPPLIER_ORDER', 'SETTLEMENT_STATEMENT'));
-- [jooq ignore stop]

-- ── Permissions ──────────────────────────────────────────────
-- Profitability and the office-payments line of the finance page reuse the
-- existing finance permission tours-operator.payment:view.

INSERT INTO wego.identity_permission (code, description) VALUES
    ('tours-operator.cost:manage', 'View and change tour cost components and driver trip rates (effective-dated; never edited, only ended and replaced)'),
    ('tours-operator.settlement:pay', 'Record supplier/driver payments up to the manager limit (5000 EGP per payment), payable adjustments, and print settlement statements'),
    ('tours-operator.settlement:approve', 'Approve a supplier/driver payment above the manager limit, and reverse settlement payments or adjustments (owner)'),
    ('tours-operator.cash-box:close', 'Count and submit the daily office cash box (reception)'),
    ('tours-operator.cash-box:confirm', 'Confirm (close) or reopen a counted cash-box day (manager)'),
    ('tours-operator.booking:refund-office', 'Record or reverse money returned on a cancelled office booking (manager approval)');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'tours-operator.cost:manage'),
    ('platform-admin', 'tours-operator.settlement:pay'),
    ('platform-admin', 'tours-operator.settlement:approve'),
    ('platform-admin', 'tours-operator.cash-box:close'),
    ('platform-admin', 'tours-operator.cash-box:confirm'),
    ('platform-admin', 'tours-operator.booking:refund-office');
