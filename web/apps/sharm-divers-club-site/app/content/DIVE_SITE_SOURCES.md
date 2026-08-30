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

Each source below was independently fetched and checked against its claim on 2026-08-30, not cited
on the strength of a suggested URL alone.

| Site | Claim | Source |
|---|---|---|
| Ras Mohammed | Sits at the southern tip of the Sinai Peninsula, where the Gulf of Suez meets the Gulf of Aqaba. | Egyptian Environmental Affairs Agency (EEAA), "Ras Mohammed" protected-area profile — states the reserve "تقع عند التقاء خليج السويس وخليج العقبة" (is located at the meeting point of the Gulf of Suez and Gulf of Aqaba) and gives its 1983 announcement date. `https://www.eeaa.gov.eg/Topics/85/36/65/Details`. Accessed 2026-08-30. |
| Tiran | Sits in the Strait of Tiran, between the Sinai Peninsula and Saudi Arabia, at the mouth of the Gulf of Aqaba. | NASA Earth Observatory, "Strait of Tiran, Red Sea and Gulf of Aqaba" — describes the strait as separating the Gulf of Aqaba from the Red Sea, between Egypt's Sinai Peninsula and Saudi Arabia. `https://science.nasa.gov/earth/earth-observatory/strait-of-tiran-red-sea-and-gulf-of-aqaba-81772/`. Accessed 2026-08-30. |
| SS Thistlegorm | A British WWII cargo ship that sank in the northern Red Sea in 1941, named one of the world's top ten wreck dives by The Times. | **Sinking:** Imperial War Museums (IWM) Film catalogue record — archival entry describing "the sinking of the British merchantman SS Thistlegorm by enemy bombing in the Red Sea on 6 October 1941." `https://film.iwmcollections.org.uk/record/32973`. **"Top ten" ranking:** Wikipedia's "SS Thistlegorm" article, which states "In 2007 *The Times* named *Thistlegorm* as one of the top ten wreck diving sites in the world" — the blurb's wording was corrected to track this citation's actual language ("top ten") rather than the looser, unsourced "best-known" phrasing an earlier draft used. `https://en.wikipedia.org/wiki/SS_Thistlegorm`. Accessed 2026-08-30. |
| Dahab Blue Hole & Canyon | Well-known dive sites on Sinai's Red Sea coast, near Dahab. | Chamber of Diving and Watersports (CDWS) — Egypt's official diving-industry body — Dahab dive-site listing naming and describing both the Blue Hole and the Canyon, with coordinates, on the Dahab coast. `https://cdws.travel/divesites/dahab`. **Corrected 2026-08-30 (round 3 re-review):** an earlier draft of this claim said "Gulf of Aqaba coast" — CDWS's page never actually names that gulf (it only says "Red Sea" throughout), so the claim was corrected to what the source actually states rather than a geographically-true-but-unsourced specific-gulf claim. Accessed 2026-08-30. |

**Reviewed and approved by:** Claude Code (implementing engineer), 2026-08-30, as part of the
independent Tier 1 review remediation round — see `docs/execution/WEGO_EXECUTION_BOARD.md`'s WEGO-011
remediation entry for the full review context.

**Re-review trigger:** any future edit to these 4 blurbs, or a decision to publish a fifth dive site,
must update this table with the same basis-and-source discipline before shipping.
