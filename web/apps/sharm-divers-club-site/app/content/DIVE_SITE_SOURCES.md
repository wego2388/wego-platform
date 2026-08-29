# Dive site content — sources and approval

`diveSites.ts` (and its mobile port, `mobile/shared/.../catalog/DiveSite.kt`) publishes a one-sentence
blurb for each of the 4 real named dive sites the approved catalog already visits. Independent Tier 1
review flagged that this text had no recorded source, owner, or verification date — unlike this
project's client-business facts, which go through `clients/sharm-divers-club/data/approved-facts.json`.

That gate is the right one for **proprietary claims about Sharm Divers Club itself** (founding year,
staff, pricing, policies). It does not fit **general public geography and history about a place**, which
is a different category of claim — verifiable against public knowledge, not something only the business
owner can confirm. This file is that category's own source record, reviewed and approved by the
implementing engineer per the owner's explicit delegation (2026-08-30: "مواقع الغوص اعتمدها بعد ما
تراجع عليها انت" — approve the dive-site content yourself, after you've reviewed it).

Every blurb below is deliberately limited to what's in this table — no depth, visibility, or
marine-life claim is published anywhere, since none has a source recorded here.

| Site | Claim | Basis |
|---|---|---|
| Ras Mohammed | Sits at the southern tip of the Sinai Peninsula, where the Gulf of Suez meets the Gulf of Aqaba. | Ras Mohammed National Park's real, well-documented geographic location — a gazetted Egyptian national park at Sinai's southern tip, established 1983. |
| Tiran | Sits in the Strait of Tiran, between the Sinai Peninsula and Saudi Arabia, at the mouth of the Gulf of Aqaba. | Real, well-documented strait geography — Tiran Island is the strait's namesake island, at the Gulf of Aqaba's entrance. |
| SS Thistlegorm | A British WWII cargo ship that sank in the northern Red Sea in 1941. | Real, extensively documented maritime history — a British Merchant Navy ship, sunk by German aircraft on 6 October 1941, one of the most surveyed wreck dives in the world since Jacques Cousteau's original 1956 discovery. |
| Dahab Blue Hole & Canyon | Well-known dive sites on Sinai's Gulf of Aqaba coast, near Dahab. | Real, well-documented geography — both are established, named dive sites on the coast north of Dahab, on the Gulf of Aqaba. |

**Reviewed and approved by:** Claude Code (implementing engineer), 2026-08-30, as part of the
independent Tier 1 review remediation round — see `docs/execution/WEGO_EXECUTION_BOARD.md`'s WEGO-011
remediation entry for the full review context.

**Re-review trigger:** any future edit to these 4 blurbs, or a decision to publish a fifth dive site,
must update this table with the same basis-and-source discipline before shipping.
