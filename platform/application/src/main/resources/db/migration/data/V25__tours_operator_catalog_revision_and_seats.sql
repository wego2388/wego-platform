-- ============================================================
-- V25 — Tours Operator: seat-accurate slot counts, owner catalog
--        revision (2026-09-30) and per-unit price options
-- ============================================================
-- DML only (see V17 for why data lives in db/migration/data).

-- 1) booked_count becomes guests, not bookings. Recompute it from the
--    bookings that hold places (NEW holds until paid or expired; CONFIRMED
--    and COMPLETED keep them). No per-unit booking exists before V24, so
--    every held booking is per person. A slot whose guests already exceed
--    its capacity keeps them: capacity is raised to fit rather than dropping
--    anyone's booking, and the slot is blocked so it takes no new sales
--    until staff review it (the raised capacity may exceed the real vehicle).
WITH held AS (
    SELECT slot_id, SUM(adults_count + children_count) AS guests
    FROM wego.tours_operator_booking
    WHERE status IN ('NEW', 'CONFIRMED', 'COMPLETED')
    GROUP BY slot_id
)
UPDATE wego.tours_operator_tour_slot AS s
SET capacity     = GREATEST(s.capacity, COALESCE(h.guests, 0)),
    booked_count = COALESCE(h.guests, 0),
    is_blocked   = s.is_blocked OR COALESCE(h.guests, 0) > s.capacity
FROM wego.tours_operator_tour_slot AS s2
LEFT JOIN held AS h ON h.slot_id = s2.id
WHERE s.id = s2.id;

-- 2) Owner catalog revision of 2026-09-30
--    (clients/safari-tours-sharm/content-research/approved-catalog.json
--    "revisions"). Each change applies only while the row still holds the
--    value it replaces, so an edit staff made in the ERP is never overwritten.
--    (Section 3 is guarded on price_basis instead: it converts a tour once.)
UPDATE wego.tours_operator_tour SET price_adult_cents = 3500 WHERE slug = 'sunset-quad-bike' AND price_adult_cents = 3000;
UPDATE wego.tours_operator_tour SET available_time_slots = 'MORNING,AFTERNOON' WHERE slug = 'double-buggy-camel-ride' AND available_time_slots = 'MORNING,SUNSET';
-- The buggy no longer runs at sunset: close future sunset departures that nobody holds.
UPDATE wego.tours_operator_tour_slot AS s
SET is_blocked = true
FROM wego.tours_operator_tour AS t
WHERE s.tour_id = t.id AND t.slug = 'double-buggy-camel-ride'
  AND s.time_slot = 'SUNSET' AND s.date >= CURRENT_DATE AND s.booked_count = 0;
UPDATE wego.tours_operator_tour SET price_adult_cents = 3500 WHERE slug = 'bedouin-dinner-camel-ride' AND price_adult_cents = 3000;
UPDATE wego.tours_operator_tour SET price_adult_cents = 4000 WHERE slug = 'ras-mohamed-white-island-boat' AND price_adult_cents = 4500;
UPDATE wego.tours_operator_tour SET price_adult_cents = 3500 WHERE slug = 'evening-cruise' AND price_adult_cents = 4000;
UPDATE wego.tours_operator_tour SET duration_text = 'About 4 hours (18:00–22:00)' WHERE slug = 'evening-cruise' AND duration_text = '2–5 hours';
UPDATE wego.tours_operator_tour SET price_adult_cents = 1500 WHERE slug = 'glass-bottom-boat' AND price_adult_cents = 2000;
UPDATE wego.tours_operator_tour
SET price_adult_cents = 2000, tour_type = 'REQUEST_ONLY', is_active = false,
    pricing_note = 'From €20 — booked on request via WhatsApp'
WHERE slug = 'intro-diving' AND price_adult_cents = 2500 AND tour_type = 'TOUR';
UPDATE wego.tours_operator_tour SET duration_text = '3 hours (private boat)' WHERE slug = 'speed-boat-adventure' AND duration_text = '30–60 minutes';
UPDATE wego.tours_operator_tour SET price_adult_cents = 4000 WHERE slug = 'mount-sinai-st-catherine' AND price_adult_cents = 5000;
UPDATE wego.tours_operator_tour SET price_adult_cents = 7000 WHERE slug = 'swimming-with-dolphins' AND price_adult_cents = 6500;
UPDATE wego.tours_operator_tour SET is_active = false WHERE slug = 'crocodile-show' AND is_active = true;

-- 3) Per-unit tours (owner workbook 2026-09-30: "Price per buggy (seats 2
--    adults)", "Price per boat", airport transfer "per vehicle: sedan €15 ·
--    SUV €20 · Hiace minibus €35"). price_adult_cents becomes the "from"
--    price shown in lists. Slot capacity is in places; a per-unit booking
--    takes every seat of the units it buys (buggy: capacity 10 = 5 buggies).
--    Seat counts confirmed by the owner on 2026-10-01.

-- Double Buggy & Camel Ride: €30 per two-seat buggy.
UPDATE wego.tours_operator_tour
SET price_basis = 'PER_UNIT', price_adult_cents = 3000, price_child_cents = NULL
WHERE slug = 'double-buggy-camel-ride' AND price_basis = 'PER_PERSON';
INSERT INTO wego.tours_operator_tour_price_option (tour_id, code, label_en, seats_per_unit, price_cents, sort_order)
SELECT id, 'buggy', 'Two-seat buggy', 2, 3000, 0 FROM wego.tours_operator_tour WHERE slug = 'double-buggy-camel-ride'
ON CONFLICT (tour_id, code) DO NOTHING;

-- Speed Boat Adventure: €150 per private boat for up to 5 guests.
UPDATE wego.tours_operator_tour
SET price_basis = 'PER_UNIT', price_adult_cents = 15000, price_child_cents = NULL, capacity = 5
WHERE slug = 'speed-boat-adventure' AND price_basis = 'PER_PERSON';
INSERT INTO wego.tours_operator_tour_price_option (tour_id, code, label_en, seats_per_unit, price_cents, sort_order)
SELECT id, 'boat', 'Private speedboat', 5, 15000, 0 FROM wego.tours_operator_tour WHERE slug = 'speed-boat-adventure'
ON CONFLICT (tour_id, code) DO NOTHING;

-- One private boat per departure: existing boat slots shrink to one boat
-- (never below what is already held).
UPDATE wego.tours_operator_tour_slot AS s
SET capacity = GREATEST(s.booked_count, LEAST(s.capacity, 5))
FROM wego.tours_operator_tour AS t
WHERE s.tour_id = t.id AND t.slug = 'speed-boat-adventure';

-- Sharm Airport Transfer: per vehicle. Sedan 4 guests, SUV 4 guests, Hiace
-- minibus 8 guests. A slot carries up to 8 places so the minibus fits.
UPDATE wego.tours_operator_tour
SET price_basis = 'PER_UNIT', price_adult_cents = 1500, price_child_cents = NULL,
    capacity = GREATEST(capacity, 8),
    pricing_note = 'Per vehicle: sedan €15 · SUV €20 · Hiace minibus €35'
WHERE slug = 'sharm-airport-transfer' AND price_basis = 'PER_PERSON';
INSERT INTO wego.tours_operator_tour_price_option (tour_id, code, label_en, seats_per_unit, price_cents, sort_order)
SELECT t.id, o.code, o.label_en, o.seats, o.price_cents, o.sort_order
FROM wego.tours_operator_tour AS t
CROSS JOIN (VALUES
    ('sedan', 'Sedan car', 4, 1500, 0),
    ('suv', 'SUV', 4, 2000, 1),
    ('minibus', 'Hiace minibus', 8, 3500, 2)
) AS o (code, label_en, seats, price_cents, sort_order)
WHERE t.slug = 'sharm-airport-transfer'
ON CONFLICT (tour_id, code) DO NOTHING;

-- Future slots of the airport transfer follow the new capacity.
UPDATE wego.tours_operator_tour_slot AS s
SET capacity = GREATEST(s.capacity, 8)
FROM wego.tours_operator_tour AS t
WHERE s.tour_id = t.id AND t.slug = 'sharm-airport-transfer';
