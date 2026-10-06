-- ============================================================
-- V31 — Tours Operator: office documents (print register)
-- ============================================================
--
-- WEGO-016-OPS2-D. Vouchers, payment receipts, the daily run sheet, pickup
-- manifests and cancellation / money-to-return forms are printed by the
-- browser from the ERP; no PDF is ever stored. What IS stored, append-only,
-- is the register of every print and reprint: which document, for which
-- subject, its immutable document number, which version (1 = original,
-- n = reprint), the language, who printed it and when. It holds no customer
-- data: the subject is only an internal id (booking, collection, slot or a
-- date) and the number a sequence or booking reference.

CREATE TABLE wego.tours_operator_document_sequence (
    document_type  varchar(24) NOT NULL,
    sequence_year  integer NOT NULL,
    last_value     bigint NOT NULL,

    PRIMARY KEY (document_type, sequence_year),
    CONSTRAINT tours_operator_document_sequence_positive CHECK (last_value >= 1)
);

COMMENT ON TABLE wego.tours_operator_document_sequence IS
    'Per-type, per-year counter for sequential document numbers (e.g. RCT-2026-000001). Incremented under row lock inside the printing transaction, so numbers are monotonic and gapless.';

CREATE TABLE wego.tours_operator_document_print (
    id                   uuid PRIMARY KEY,
    document_type        varchar(24) NOT NULL,
    subject_key          varchar(64) NOT NULL,
    document_number      varchar(40) NOT NULL,
    version              integer NOT NULL,
    language             varchar(2) NOT NULL,
    printed_by_user_id   uuid REFERENCES wego.identity_user (id) ON DELETE SET NULL,
    printed_at           timestamp with time zone NOT NULL,
    -- Run sheet / pickup manifest only: SHA-256 of the PII-free row ids, counts and flags that were printed,
    -- so a later print can tell "same content" (COPY) from "changed" (REVISED).
    content_fingerprint  varchar(64),

    CONSTRAINT tours_operator_document_print_type_known
        CHECK (document_type IN ('VOUCHER', 'RECEIPT', 'RUN_SHEET', 'PICKUP_MANIFEST', 'CANCELLATION_FORM')),
    CONSTRAINT tours_operator_document_print_fingerprint_shape
        CHECK (content_fingerprint IS NULL OR content_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT tours_operator_document_print_version_positive CHECK (version >= 1),
    CONSTRAINT tours_operator_document_print_language_known CHECK (language IN ('en', 'ar')),
    CONSTRAINT tours_operator_document_print_subject_shape
        CHECK (subject_key = btrim(subject_key) AND length(subject_key) >= 1 AND subject_key !~ '[[:cntrl:]]'),
    CONSTRAINT tours_operator_document_print_version_unique
        UNIQUE (document_type, subject_key, version)
);

-- One number per subject, never reused for another subject: only the
-- original (version 1) allocates a number, reprints carry it unchanged.
CREATE UNIQUE INDEX tours_operator_document_print_number_unique
    ON wego.tours_operator_document_print (document_type, document_number)
    WHERE version = 1;

CREATE INDEX tours_operator_document_print_subject_idx
    ON wego.tours_operator_document_print (document_type, subject_key, version);

-- The guard is PostgreSQL-only (PL/pgSQL), so jOOQ's DDL parser skips it.
-- [jooq ignore start]
-- Append-only: no edits, no deletes, no TRUNCATE. The only permitted change is the database
-- detaching a deleted staff user: ON DELETE SET NULL runs as a nested (cascade) trigger, so
-- pg_trigger_depth() > 1; a direct UPDATE (depth 1) is always refused.
CREATE FUNCTION wego.tours_operator_document_print_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND pg_trigger_depth() > 1
       AND NEW.printed_by_user_id IS NULL
       AND (to_jsonb(NEW) - 'printed_by_user_id') = (to_jsonb(OLD) - 'printed_by_user_id') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'tours_operator_document_print is append-only';
END;
$$;

CREATE TRIGGER tours_operator_document_print_append_only
    BEFORE UPDATE OR DELETE ON wego.tours_operator_document_print
    FOR EACH ROW EXECUTE FUNCTION wego.tours_operator_document_print_guard();

CREATE TRIGGER tours_operator_document_print_no_truncate
    BEFORE TRUNCATE ON wego.tours_operator_document_print
    FOR EACH STATEMENT EXECUTE FUNCTION wego.tours_operator_document_print_guard();
-- [jooq ignore stop]

COMMENT ON TABLE wego.tours_operator_document_print IS
    'Append-only register of every printed or reprinted office document (no PDF and no customer data stored). Version 1 is the original; later versions are reprints that keep the same immutable document number.';

INSERT INTO wego.identity_permission (code, description) VALUES
    ('tours-operator.document:print', 'Print or reprint customer documents: booking voucher, payment receipt, cancellation / money-to-return form'),
    ('tours-operator.document:print-ops', 'Print or reprint operations documents: daily run sheet and pickup manifest (the manifest carries customer phone numbers)');

INSERT INTO wego.identity_role_permission (role_code, permission_code) VALUES
    ('platform-admin', 'tours-operator.document:print'),
    ('platform-admin', 'tours-operator.document:print-ops');
