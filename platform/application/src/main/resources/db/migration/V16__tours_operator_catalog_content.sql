-- ============================================================
-- V16 — Tours Operator: catalog content model
-- ============================================================
-- Adds multilingual name, tour type, image, and cancellation
-- policy columns to the tour catalog. All new columns are
-- nullable or have defaults so existing test seeds remain valid.
--
-- Seed data is in V17 (DML separated from DDL for jOOQ codegen
-- compatibility — DDLDatabase uses H2 and cannot simulate
-- ON CONFLICT ... DO NOTHING in INSERT statements).
-- ============================================================

ALTER TABLE wego.tours_operator_tour
    ADD COLUMN name_en             text,
    ADD COLUMN name_ar             text,
    ADD COLUMN name_ru             text,
    ADD COLUMN name_it             text,
    ADD COLUMN description_en      text,
    ADD COLUMN short_desc_en       text,
    ADD COLUMN tour_type           varchar(20) NOT NULL DEFAULT 'TOUR',
    ADD COLUMN image_url           text,
    ADD COLUMN pricing_note        text,
    ADD COLUMN cancellation_policy varchar(20) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE wego.tours_operator_tour
    ADD CONSTRAINT tours_operator_tour_type_known
        CHECK (tour_type IN ('TOUR', 'TRANSFER', 'REQUEST_ONLY')),
    ADD CONSTRAINT tours_operator_cancellation_policy_known
        CHECK (cancellation_policy IN ('STANDARD', 'NON_REFUNDABLE', 'FLEXIBLE'));

COMMENT ON COLUMN wego.tours_operator_tour.tour_type IS
    'TOUR = standard bookable tour; TRANSFER = airport/point-to-point; REQUEST_ONLY = price on request, not in paid booking flow.';
COMMENT ON COLUMN wego.tours_operator_tour.cancellation_policy IS
    'STANDARD = 48h full / 24-48h 50% / <24h none; FLEXIBLE = free until 24h; NON_REFUNDABLE = no refund.';
