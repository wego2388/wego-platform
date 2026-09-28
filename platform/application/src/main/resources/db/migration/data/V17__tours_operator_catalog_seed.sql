-- ============================================================
-- V17 — Tours Operator: approved catalog seed
-- ============================================================
-- Seeds all 30 owner-approved tours from:
--   clients/safari-tours-sharm/content-research/approved-catalog.json
--
-- Owner authorization: 2026-09-28
-- Prices are in EUR cents (bigint). Private Boat = REQUEST_ONLY/inactive.
-- ON CONFLICT (slug) DO NOTHING — safe to replay on existing data.
--
-- This file is DML-only and intentionally separated from V16 (DDL)
-- so that jOOQ DDLDatabase (which uses H2 simulation) only sees
-- schema-altering statements.
-- ============================================================

INSERT INTO wego.tours_operator_tour (
    id, slug, category, duration_text,
    price_adult_cents, price_child_cents,
    capacity, available_time_slots,
    sort_order, is_active, created_at,
    name_en, tour_type, image_url, cancellation_policy
) VALUES
    -- 1 Super Safari Adventure
    ('a0000001-0000-0000-0000-000000000001',
     'super-safari-adventure', 'DESERT', '5–6 hours',
     3500, NULL, 20, 'MORNING,SUNSET', 1, true, now(),
     'Super Safari Adventure', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA0015-300x225.jpg',
     'STANDARD'),

    -- 2 Sunset Quad Bike
    ('a0000001-0000-0000-0000-000000000002',
     'sunset-quad-bike', 'DESERT', '2–3 hours',
     3000, NULL, 15, 'SUNSET', 2, true, now(),
     'Sunset Quad Bike', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/quad-biking-tour-in-sharm-el-sheikh-desert-2436382-300x200.webp',
     'STANDARD'),

    -- 3 Double Buggy & Camel Ride
    ('a0000001-0000-0000-0000-000000000003',
     'double-buggy-camel-ride', 'DESERT', '2–3 hours',
     4500, NULL, 10, 'MORNING,SUNSET', 3, true, now(),
     'Double Buggy & Camel Ride', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA0073-300x200.jpg',
     'STANDARD'),

    -- 4 Bedouin Dinner & Camel Ride
    ('a0000001-0000-0000-0000-000000000004',
     'bedouin-dinner-camel-ride', 'DESERT', '3–4 hours',
     3000, NULL, 25, 'SUNSET', 4, true, now(),
     'Bedouin Dinner & Camel Ride', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA0027-300x225.jpg',
     'STANDARD'),

    -- 5 Dahab 5x1 / Colored Canyon
    ('a0000001-0000-0000-0000-000000000005',
     'dahab-colored-canyon', 'CULTURAL', '8–10 hours',
     3500, NULL, 20, 'MORNING', 5, true, now(),
     'Dahab 5x1 / Colored Canyon', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/FB_IMG_1731410704834-221x300.jpg',
     'STANDARD'),

    -- 6 Camel & Horse Ride
    ('a0000001-0000-0000-0000-000000000006',
     'camel-horse-ride', 'DESERT', '1 hour',
     2500, NULL, 20, 'MORNING,AFTERNOON,SUNSET', 6, true, now(),
     'Camel & Horse Ride', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/83-300x200.jpg',
     'STANDARD'),

    -- 7 Ras Mohamed & White Island Boat Trip
    ('a0000001-0000-0000-0000-000000000007',
     'ras-mohamed-white-island-boat', 'SEA', '8–9 hours',
     4500, NULL, 30, 'MORNING', 7, true, now(),
     'Ras Mohamed & White Island Boat Trip', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA0156-300x169.jpg',
     'STANDARD'),

    -- 8 Evening Cruise
    ('a0000001-0000-0000-0000-000000000008',
     'evening-cruise', 'SEA', '2–5 hours',
     4000, NULL, 40, 'SUNSET', 8, true, now(),
     'Evening Cruise', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA0166-300x231.jpg',
     'STANDARD'),

    -- 9 Glass Bottom Boat
    ('a0000001-0000-0000-0000-000000000009',
     'glass-bottom-boat', 'SEA', '1.5 hours',
     2000, NULL, 30, 'MORNING,AFTERNOON', 9, true, now(),
     'Glass Bottom Boat', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA0069-201x300.jpg',
     'STANDARD'),

    -- 10 Diving Course
    ('a0000001-0000-0000-0000-000000000010',
     'diving-course', 'SEA', '2–3 days',
     40000, NULL, 8, 'MORNING', 10, true, now(),
     'Diving Course', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/PPB1-TaeMHJYU-1024x576-1-300x169.jpg',
     'NON_REFUNDABLE'),

    -- 11 Intro Diving
    ('a0000001-0000-0000-0000-000000000011',
     'intro-diving', 'SEA', '15 minutes underwater',
     2500, NULL, 10, 'MORNING,AFTERNOON', 11, true, now(),
     'Intro Diving', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA0165-300x225.jpg',
     'STANDARD'),

    -- 12 Private Boat (REQUEST_ONLY — inactive, price on request)
    ('a0000001-0000-0000-0000-000000000012',
     'private-boat', 'SEA', '4–8 hours',
     0, NULL, 20, 'MORNING', 12, false, now(),
     'Private Boat', 'REQUEST_ONLY',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA0190-300x225.jpg',
     'STANDARD'),

    -- 13 Sinai Dream Sailboat
    ('a0000001-0000-0000-0000-000000000013',
     'sinai-dream-sailboat', 'SEA', '5–8 hours',
     6500, NULL, 25, 'MORNING', 13, true, now(),
     'Sinai Dream Sailboat', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/pirates-boat-cruise.jpg',
     'STANDARD'),

    -- 14 Ras Mohamed by Bus
    ('a0000001-0000-0000-0000-000000000014',
     'ras-mohamed-by-bus', 'SEA', '4–6 hours',
     3000, NULL, 30, 'MORNING', 14, true, now(),
     'Ras Mohamed by Bus', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA01671-225x300.jpg',
     'STANDARD'),

    -- 15 Banana Boat
    ('a0000001-0000-0000-0000-000000000015',
     'banana-boat', 'SEA', '10–15 minutes',
     2500, NULL, 8, 'MORNING,AFTERNOON', 15, true, now(),
     'Banana Boat', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA0130-300x199.jpg',
     'STANDARD'),

    -- 16 Parasailing Adventure
    ('a0000001-0000-0000-0000-000000000016',
     'parasailing-adventure', 'SEA', '6–10 minutes flight',
     2500, NULL, 2, 'MORNING,AFTERNOON', 16, true, now(),
     'Parasailing Adventure', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2025/11/IMG-20240419-WA0102-300x199.jpg',
     'STANDARD'),

    -- 17 Speed Boat Adventure
    ('a0000001-0000-0000-0000-000000000017',
     'speed-boat-adventure', 'SEA', '30–60 minutes',
     10000, NULL, 8, 'MORNING,AFTERNOON', 17, true, now(),
     'Speed Boat Adventure', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/sharm-el-sheikh-2-hours-private-speed-boat-to-tiran-island-740580-300x199.jpg',
     'STANDARD'),

    -- 18 Submarine
    ('a0000001-0000-0000-0000-000000000018',
     'submarine', 'SEA', '1.5–2 hours',
     3500, NULL, 30, 'MORNING,AFTERNOON', 18, true, now(),
     'Submarine', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/sharm-el-sheikh-seascope-subma17373741644-300x183.webp',
     'STANDARD'),

    -- 19 Tiran Island Boat Trip
    ('a0000001-0000-0000-0000-000000000019',
     'tiran-island-boat-trip', 'SEA', '8–9 hours',
     4500, NULL, 30, 'MORNING', 19, true, now(),
     'Tiran Island Boat Trip', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/2000x2000-0-70-8da5c8a9db726301dd34107cb7334fee-300x197.jpg',
     'STANDARD'),

    -- 20 Tube Boat
    ('a0000001-0000-0000-0000-000000000020',
     'tube-boat', 'SEA', '10–15 minutes',
     2500, NULL, 4, 'MORNING,AFTERNOON', 20, true, now(),
     'Tube Boat', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/31ca45b4-8e30-4d89-9f1d-fe4d6612e8fb_sea-fun-glass-bottom-boat-tube-ride-parasailing-sharm-el-sheikh-300x200.png',
     'STANDARD'),

    -- 21 Cairo by Bus
    ('a0000001-0000-0000-0000-000000000021',
     'cairo-by-bus', 'CULTURAL', '20–24 hours',
     7500, NULL, 30, 'MORNING', 21, true, now(),
     'Cairo by Bus', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/61-300x200.jpg',
     'STANDARD'),

    -- 22 Cairo by Plane
    ('a0000001-0000-0000-0000-000000000022',
     'cairo-by-plane', 'CULTURAL', '20–24 hours',
     28000, NULL, 20, 'MORNING', 22, true, now(),
     'Cairo by Plane', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/Cairo-Trip-by-Plane-Sharm-El-Sheikh-e1669435434944-300x169.webp',
     'NON_REFUNDABLE'),

    -- 23 Mount Sinai & St. Catherine
    ('a0000001-0000-0000-0000-000000000023',
     'mount-sinai-st-catherine', 'CULTURAL', '15–16 hours',
     5000, NULL, 25, 'MORNING', 23, true, now(),
     'Mount Sinai & St. Catherine', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/FB_IMG_1754255597368-225x300.jpg',
     'STANDARD'),

    -- 24 Luxor by Plane
    ('a0000001-0000-0000-0000-000000000024',
     'luxor-by-plane', 'CULTURAL', '20–24 hours',
     28000, NULL, 20, 'MORNING', 24, true, now(),
     'Luxor by Plane', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/Excursions-from-Sharm-J-300x300.webp',
     'NON_REFUNDABLE'),

    -- 25 St. Catherine & Dahab
    ('a0000001-0000-0000-0000-000000000025',
     'st-catherine-dahab', 'CULTURAL', '11–12 hours',
     4500, NULL, 25, 'MORNING', 25, true, now(),
     'St. Catherine & Dahab', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/saint-catherine-s-monastery-300x200.jpg',
     'STANDARD'),

    -- 26 Turkish Bath
    ('a0000001-0000-0000-0000-000000000026',
     'turkish-bath', 'SHOWS', '1–2 hours',
     4500, NULL, 20, 'MORNING,AFTERNOON', 26, true, now(),
     'Turkish Bath', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/42-300x200.jpg',
     'STANDARD'),

    -- 27 Dolphin Show
    ('a0000001-0000-0000-0000-000000000027',
     'dolphin-show', 'SHOWS', '1 hour',
     2000, NULL, 50, 'MORNING,AFTERNOON', 27, true, now(),
     'Dolphin Show', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/2016130396531-300x200.jpg',
     'STANDARD'),

    -- 28 Swimming with Dolphins
    ('a0000001-0000-0000-0000-000000000028',
     'swimming-with-dolphins', 'SHOWS', '15 minutes',
     6500, NULL, 10, 'MORNING,AFTERNOON', 28, true, now(),
     'Swimming with Dolphins', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/swim-with-dolphins-activity-sharm-300x199.webp',
     'STANDARD'),

    -- 29 Crocodile Show
    ('a0000001-0000-0000-0000-000000000029',
     'crocodile-show', 'SHOWS', '1 hour',
     2000, NULL, 50, 'MORNING,AFTERNOON', 29, true, now(),
     'Crocodile Show', 'TOUR',
     'https://safaritourssharm.com/wp-content/uploads/2026/01/crocodile-snake-sharm-trip-day-show-34-300x225.jpg',
     'STANDARD'),

    -- 30 Sharm Airport Transfer
    ('a0000001-0000-0000-0000-000000000030',
     'sharm-airport-transfer', 'TRANSFERS', '30–60 minutes',
     1500, NULL, 4, 'SUNRISE,MORNING,AFTERNOON,SUNSET', 30, true, now(),
     'Sharm Airport Transfer', 'TRANSFER',
     NULL,
     'STANDARD')

ON CONFLICT (slug) DO NOTHING;
