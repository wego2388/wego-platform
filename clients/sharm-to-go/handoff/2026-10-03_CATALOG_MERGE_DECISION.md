# Sharm To Go — catalog merge decision (owner-approved, 2026-10-03)

Settles `ROADMAP_AR.md`/`2026-10-02_HANDOFF_REVIEW_DESIGN_AND_CATALOG.md`'s
open catalog-merge question. This is a decision record, not a code change —
`task #66` (the import pipeline) and `task #65` (original content) consume
this list.

## Owner decisions, this session (2026-10-03)

1. **Currency:** stored price stays EGP only (zero schema change). USD is a
   display-only approximation, computed from one named constant derived
   from the owner's own 2026-09-20 EUR/EGP launch rate (59.80), not an
   independent guess. See `usePublicCatalog.ts`'s `APPROXIMATE_EGP_PER_USD`.
   The same 59.80 EGP/EUR rate is reused at import time to convert the 24
   new Safari trips' approved EUR prices into real EGP prices — one
   consistent rate across the whole catalog, not two different ones.
2. **Diving/dolphin:** excluded from this import (`diving-course`,
   `dolphin-show`, `swimming-with-dolphins`). Owner will add these himself
   later, in an ordinary category, not a bundled package.
3. **Redundant old bundles:** retired in favor of the cleaner, individually-
   priced new equivalents (owner's explicit confirmation).
4. **Marketing content:** rewritten from scratch in Sharm To Go's own voice
   (tourism, fun, visitor's-guide framing) — never reworded/translated
   Safari copy. Facts (price, duration, inclusions) stay identical to the
   approved source; only the words change. Tracked separately as task #65.
5. **Old 37 vs new 27:** add the new on top of the old, no duplicates,
   maximizing total distinct trip count.

## Important finding, not previously flagged: the old 17 are far more
## launch-ready than the original review assumed

`SHARM_TO_GO_SERVICE_INTAKE_SHEETS.md`'s later sections (2026-09-20 and
2026-09-21) already settled, at the owner's own explicit direction:
`DIRECT` fulfilment for all 37, confirmation type per concept (`INSTANT`
except 5 flight/ferry/hotel-dependent ones kept at `STAFF_REVIEW`),
EGP prices (the same 59.80 EGP/EUR formula), a two-tier cancellation
policy with exact EN/AR wording, and capacity/schedule/safety/inclusions
for every concept. The only genuine blocker left for the 17 kept old
concepts is the same one every service in this project already carries
honestly: **no real, rights-cleared photo yet** — which blocks `publish()`
specifically, not `create`/`submit-for-review`/`approve`. Plan: import all
41 through Draft → Review → Approved now; leave the final Publish click
(with the project's existing mockup-media convention, or real photos once
they exist) for the owner's own call, same as every other service in this
catalog.

## Final merged list — 41 distinct trips, zero duplicates

### Incoming from Safari (24 of 27 — diving/dolphin excluded)

| Slug | Name |
|---|---|
| super-safari-adventure | Super Safari Adventure |
| sunset-quad-bike | Sunset Quad Bike |
| double-buggy-camel-ride | Double Buggy & Camel Ride |
| bedouin-dinner-camel-ride | Bedouin Dinner & Camel Ride |
| dahab-colored-canyon | Dahab 5×1 / Colored Canyon |
| camel-horse-ride | Camel & Horse Ride |
| ras-mohamed-white-island-boat | Ras Mohamed & White Island Boat Trip |
| evening-cruise | Evening Cruise |
| glass-bottom-boat | Glass Bottom Boat |
| sinai-dream-sailboat | Sinai Dream Sailboat |
| ras-mohamed-by-bus | Ras Mohamed by Bus |
| banana-boat | Banana Boat |
| parasailing-adventure | Parasailing Adventure |
| speed-boat-adventure | Speed Boat Adventure |
| submarine | Submarine |
| tiran-island-boat-trip | Tiran Island Boat Trip |
| tube-boat | Tube Boat |
| cairo-by-bus | Cairo by Bus |
| cairo-by-plane | Cairo by Plane |
| mount-sinai-st-catherine | Mount Sinai & St. Catherine |
| luxor-by-plane | Luxor by Plane |
| st-catherine-dahab | St. Catherine & Dahab |
| turkish-bath | Turkish Bath |
| sharm-airport-transfer | Sharm Airport Transfer |

**Excluded (owner will add later, as ordinary services, not a package):**
`diving-course`, `dolphin-show`, `swimming-with-dolphins`.

### Kept from the old 37 (17 — no equivalent among the new 24, or distinct enough to keep anyway)

| ID | Name | Why kept |
|---|---|---|
| STG-TRN-002 | Private Venue Return Transfer | No equivalent |
| STG-FAM-001 | Aqua Park Day | No equivalent |
| STG-DSR-005 | Sunrise or Morning Quad Safari | Distinct timing vs. the new sunset-only quad trip |
| STG-PT-001 | Private Sharm City Essentials | No equivalent |
| STG-PT-002 | Sharm Museum and City Choices | No equivalent |
| STG-PT-003 | Sharm City and Parasailing | City-sightseeing + activity combo, not a pure duplicate |
| STG-PT-004 | City Sights and Desert Adventure | Same reasoning as PT-003 |
| STG-PT-005 | Sharm Highlights and Seafood Meal | No equivalent |
| STG-PT-006 | SOHO Square and Hollywood Park Transfer | No equivalent |
| STG-PT-007 | Private Car and Driver in Sharm | No equivalent |
| STG-PT-008 | Private ATV Desert Experience | No equivalent |
| STG-PT-012 | Private Dahab and Canyon Adventure | No equivalent (distinct from the new dahab-colored-canyon) |
| STG-PT-013 | Private Cairo Pyramids and Museum Day | No equivalent (distinct from the new single-day Cairo trips) |
| STG-PT-014 | Giza, Saqqara, Memphis and Khan El-Khalili | No equivalent |
| STG-DAY-004 | Petra by Ferry & Coach | No equivalent — not in the Safari 27 at all |
| STG-MUL-001 | Two-Day Cairo by Air | No equivalent (multi-day) |
| STG-MUL-002 | Two-Day Dahab & Canyon Camp | No equivalent (multi-day) |

### Retired (20 — old concept drops, new approved version or nothing replaces it)

**Clear 1:1 duplicates (13)** — new approved version replaces: STG-SEA-001,
STG-SEA-002, STG-SEA-003, STG-NAT-001, STG-CUL-001, STG-CUL-002,
STG-TRN-001, STG-DAY-001, STG-DAY-002, STG-DAY-003, STG-NAT-002,
STG-DSR-002, STG-DSR-003.

**Redundant bundles, owner-approved retirement (7)** — superseded by
cleaner individually-priced new trips: STG-DSR-001, STG-DSR-004, STG-PT-009,
STG-PT-010, STG-PT-011, STG-SEA-004, STG-SEA-005.

## Verification

- 27 Safari slugs confirmed directly from `catalog-facts.json` (not just
  the review's summary).
- All 37 old concept IDs/names confirmed directly from
  `SHARM_TO_GO_SERVICE_INTAKE_SHEETS.md`'s own headers.
- Retained-17 list derived by direct subtraction (37 − 13 − 7 = 17) and
  cross-checked name-by-name against the full source list — not taken
  verbatim from the earlier research agent's own summary, which under-
  enumerated its own "zero overlap" bucket (listed 11 of the real 11, but
  grouped 3 of them, STG-PT-008/012/013, under a different sub-count by
  mistake; the math (37 total) still checks out here).
