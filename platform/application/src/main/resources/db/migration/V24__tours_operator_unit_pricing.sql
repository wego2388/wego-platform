-- ============================================================
-- V24 — Tours Operator: per-unit pricing and seat-accurate capacity
-- ============================================================
--
-- Some tours are sold by the unit, not by the person: a two-seat buggy, a
-- private speedboat, an airport car or minibus. Such a tour has
-- price_basis = 'PER_UNIT' and one or more price options; a booking then
-- buys unit_count units of one option and the guests must fit in them.
--
-- Slot booked_count now counts places, not bookings: one per guest, or for a
-- per-unit booking every seat of every unit bought.
-- The data migration V25 recomputes it for existing bookings.

ALTER TABLE wego.tours_operator_tour
    ADD COLUMN price_basis varchar(16) NOT NULL DEFAULT 'PER_PERSON';

ALTER TABLE wego.tours_operator_tour
    ADD CONSTRAINT tours_operator_tour_price_basis_known
        CHECK (price_basis IN ('PER_PERSON', 'PER_UNIT'));

CREATE TABLE wego.tours_operator_tour_price_option (
    tour_id         uuid        NOT NULL
                        REFERENCES wego.tours_operator_tour (id) ON DELETE CASCADE,
    code            varchar(40) NOT NULL,
    label_en        varchar(80) NOT NULL,
    seats_per_unit  integer     NOT NULL,
    price_cents     bigint      NOT NULL,
    sort_order      integer     NOT NULL DEFAULT 0,

    PRIMARY KEY (tour_id, code),
    CONSTRAINT tours_operator_tour_price_option_code_format
        CHECK (code ~ '^[a-z0-9][a-z0-9-]{0,39}$'),
    CONSTRAINT tours_operator_tour_price_option_label_not_blank
        CHECK (length(trim(label_en)) > 0),
    CONSTRAINT tours_operator_tour_price_option_seats_range
        CHECK (seats_per_unit BETWEEN 1 AND 99),
    CONSTRAINT tours_operator_tour_price_option_price_positive
        CHECK (price_cents > 0),
    CONSTRAINT tours_operator_tour_price_option_sort_nonnegative
        CHECK (sort_order >= 0)
);

-- A per-unit booking snapshots what it bought: all four columns are set
-- together, or none are (per-person booking).
ALTER TABLE wego.tours_operator_booking
    ADD COLUMN price_option_code  varchar(40),
    ADD COLUMN price_option_label varchar(80),
    ADD COLUMN seats_per_unit     integer,
    ADD COLUMN unit_count         integer,
    ADD COLUMN unit_price_eur     numeric(10, 2);

ALTER TABLE wego.tours_operator_booking
    ADD CONSTRAINT tours_operator_booking_unit_all_or_none
        CHECK (
            (price_option_code IS NULL AND price_option_label IS NULL AND seats_per_unit IS NULL
                AND unit_count IS NULL AND unit_price_eur IS NULL)
            OR (price_option_code IS NOT NULL AND price_option_label IS NOT NULL AND seats_per_unit IS NOT NULL
                AND unit_count IS NOT NULL AND unit_price_eur IS NOT NULL)
        );

ALTER TABLE wego.tours_operator_booking
    ADD CONSTRAINT tours_operator_booking_unit_values
        CHECK (
            unit_count IS NULL
            OR (unit_count >= 1 AND seats_per_unit >= 1 AND unit_price_eur >= 0
                AND adults_count + children_count <= unit_count * seats_per_unit
                AND unit_count <= adults_count + children_count
                AND price_adult_eur = 0 AND price_child_eur IS NULL)
        );

-- The total always follows from the stored snapshot.
ALTER TABLE wego.tours_operator_booking
    DROP CONSTRAINT tours_operator_booking_total_matches_pricing;

ALTER TABLE wego.tours_operator_booking
    ADD CONSTRAINT tours_operator_booking_total_matches_pricing
        CHECK (
            (unit_count IS NULL
                AND total_eur = (price_adult_eur * adults_count) + (COALESCE(price_child_eur, 0) * children_count))
            OR (unit_count IS NOT NULL AND total_eur = unit_price_eur * unit_count)
        );
