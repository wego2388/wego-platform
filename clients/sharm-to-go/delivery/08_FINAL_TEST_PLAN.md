# Sharm To Go — final pre-launch test plan (language × device × request status)

Referenced by `ROADMAP_AR.md`'s Phase 6 item 6-6 ("خطة تجربة نهائية
(لغة × جهاز × حالة طلب) وإطلاق تدريجي مراقَب") and
`delivery/06_LAUNCH_AND_OPERATIONS.md`'s "End-to-end staging journey passes
across web, ERP and mobile" release-gate line.

**Why this exists:** every verification this project has done so far has
been real server + real database + real HTTP, or `@vue/test-utils`
component tests running the real page code — never a real browser
rendering a real page on a real screen. That is a genuine, honestly
recorded gap (see `03_MARKETING_WEBSITE.md`), not a claim of completeness.
This document is the checklist that closes it: a human (or an agent with
real browser access) walks through it once, start to finish, before launch.

**How to use it:** copy this file's result table (or print it) and mark
each cell pass/fail with a date and who ran it. A cell that cannot be
reached yet (e.g. no `CONFIRMED` request exists in staging) stays blank,
not checked — never mark a cell done from reasoning about the code instead
of actually doing it.

## 1. Core journey × language × device (highest priority — run this first)

The single most important path end to end: homepage search → catalog →
service detail → request form (all 4 steps) → submission → confirmation →
tracking page. Run it as a real visitor would, on a real rendered page.

| # | Language | Device | Result | Notes |
|---|---|---|---|---|
| 1 | English | Mobile (~375px wide, e.g. a real phone or devtools device mode) | | |
| 2 | English | Desktop (~1280px+) | | |
| 3 | Arabic (RTL) | Mobile | | Confirm the whole layout mirrors correctly, not just the text |
| 4 | Arabic (RTL) | Desktop | | |

For every cell, confirm all of the following within that one pass:

- [ ] Homepage search box: category dropdown populates from the real
      catalog, date picker rejects past dates, guest steppers have real
      accessible Decrease/Increase controls (not "Back"/"Continue").
- [ ] Submitting the search box routes to the catalog filtered by the
      chosen category, with the date/party carried through.
- [ ] Catalog list shows real published services only, with real prices.
- [ ] Selecting a service opens the real detail page — price, cancellation
      policy, pickup info, inclusions/exclusions all match what the ERP
      shows for that same service.
- [ ] "Request this experience" opens the real request form at step 1
      (party & date), not the old booking-preview mock.
- [ ] Step 1 → step 2 (contact) requires a valid date and a party size
      within the selected option's capacity.
- [ ] Step 2 requires a name and at least one of phone/email, rejects an
      implausible phone number, and **requires the consent checkbox** —
      try submitting without checking it and confirm the real error shows.
- [ ] Step 3 (review) shows the exact service, date, party, price and
      policy that will actually be submitted.
- [ ] Submitting creates a real request — confirm the resulting reference
      (`STG-XXXXXXXX`) also appears in the ERP's request queue within a
      few seconds.
- [ ] The result screen's heading/body honestly distinguishes an
      `INSTANT` service (confirmed immediately) from a `STAFF_REVIEW` one
      (received, awaiting staff) — test one of each if the catalog has
      both.
- [ ] Copy-summary and WhatsApp-share buttons on the result screen work
      and produce a real, readable message.
- [ ] The tracking link (`/track/:reference`) shows the same real status.
- [ ] Mobile only: the hamburger menu opens, shows all 5 nav links, and
      closes itself after choosing one.
- [ ] Keyboard-only pass (no mouse): tab from the very top of the page —
      the skip-to-content link should be the first focusable element and
      visibly appear on focus.
- [ ] No layout breakage, overlapping text, or horizontal scroll at the
      tested width in either direction (LTR/RTL).

## 2. Request status coverage (once per language, device secondary)

Each real `TravelRequestStatus` renders correctly everywhere it is shown
to a customer or to staff. Run once in English, once in Arabic; desktop is
enough here — this section is about correctness of status handling, not
responsive layout (already covered in section 1).

| Status | How to reach it | Site tracking page | ERP request detail | Result |
|---|---|---|---|---|
| `NEW` | Submit a request against a `STAFF_REVIEW` service | Shows "received, awaiting confirmation" wording, not "confirmed" | Shows real customer/request data, review/cancel actions available | |
| `IN_REVIEW` | In the ERP, click "Start review" on a `NEW` request | Still shows the honest awaiting-confirmation wording | Confirm/cancel actions available | |
| `CONFIRMED` | Submit against an `INSTANT` service, or confirm an `IN_REVIEW` one in the ERP | Shows the real confirmation heading | Shows confirmed state, complete/cancel actions available | |
| `COMPLETED` | In the ERP, mark a `CONFIRMED` request complete | Shows a completed state, not still "confirmed" | No further actions offered | |
| `CANCELLED` | In the ERP, cancel a request with a reason | Shows cancelled, not a generic error | Shows the real cancellation reason and who cancelled it | |
| `EXPIRED` | Either wait for the real 15-minute scheduler sweep on an overdue `NEW`/`IN_REVIEW` request in staging, or (faster) run `expireTravelRequestsScheduler.run()` directly against a request whose `requestedDate` you've set in the past | Shows expired, not a generic error | Shows expired state | |

For each row, also confirm the ERP's own audit timeline on that request
shows every real transition, with correct actor (staff name or "System"
for the scheduler) and timestamp.

## 3. ERP catalog-editor concurrency (once, either language)

Proves the optimistic-locking fix added 2026-10-02, with two real browser
tabs/sessions — not two terminal `curl` calls (already proven that way in
the automated tests; this is the human-visible version).

- [ ] Open the same service/provider/category for edit in two separate
      ERP tabs (can be the same staff login in both).
- [ ] Save a change in tab A — succeeds.
- [ ] Without reloading tab B, save a *different* change there —
      confirm tab B shows a real "someone else saved changes" error, not
      a silent overwrite of tab A's change.
- [ ] Reload tab B, redo the edit, save — succeeds.

## 4. Staged, monitored rollout (after section 1-3 all pass)

Not a test case — the launch sequence itself, once every cell above
passes:

1. Deploy to the real domain per `infrastructure/SHARM_TO_GO_VPS.md`.
2. Run `scripts/sharm-to-go-health.sh <site-domain> <admin-domain>`
   immediately after — all four checks must pass before telling anyone
   the site is live.
3. Soft-launch: share the real link with a small, known group first (e.g.
   the owner's own contacts or a handful of past guests), not a public
   announcement — watch the ERP queue and the health check closely for the
   first real requests before wider promotion.
4. Only after real requests have been created, confirmed and completed
   without incident, widen to full public announcement.

## Result log

Append one dated entry per full pass through sections 1-3, in the same
append-only style as `07_ACCEPTANCE_AND_EVIDENCE.md` — never overwrite a
prior entry.

```md
### YYYY-MM-DD — run by <who>

- Section 1: <pass/fail summary, link any screenshots if captured>
- Section 2: <pass/fail summary>
- Section 3: <pass/fail summary>
- Issues found: <list, or "none">
```
