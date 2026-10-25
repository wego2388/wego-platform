# SAF-04 Finance Decision Memo (analysis only)

- **Date:** 2026-10-25 · **Author:** Claude (read-only analysis; no code, migration, commit or server access)
- **Scope:** SAF-04.01, .03, .04, .05, .06 of `2026-10-23_SAFARI_MASTER_EXECUTION_HANDOFF_AR.md` §10. SAF-04.02 (hub vs supplier agreements) is not covered here, but it is a **precondition** for freezing costs (§2).
- **Basis:** a static read of the OPS2-F code (V33, `CostService`, `ProfitabilityService`, `Profitability.kt`, `PayablesService`, `Payables.kt`, `FinanceOps.kt`, `CashBoxService`, `OfficeCollectionService`, `OfficeRefundService`, `JooqFinanceOpsRepositories`, `FinanceOpsController`, `StaffBookingController`, `HandlePaymobWebhookService`, ERP finance pages) and the OPS2-F board entries. No tests were run; every scenario below must be turned into a failing test before it is fixed.
- Paths are shortened: `app/` = `products/tours-operator/src/main/kotlin/com/wego/toursoperator/application`, `dom/` = `.../domain`, `infra/` = `.../infrastructure`, `erp/` = `web/apps/safari-tours-sharm-erp/app`.

---

## 0. Priorities (TL;DR)

| # | Item | Severity | Fix in |
|---|------|----------|--------|
| D1 | Profit is based on **collected** cash, with no booked value or outstanding shown: unpaid office balances show as trusted losses | High | Slice B (report) + owner decision Q1 |
| D3 | A missing **supplier** cost is not flagged when the tour has some other cost: profit is overstated and labelled reliable | High | Slice A |
| G1 | **F6 self-dealing loop**: one `settlement:pay` holder can raise a CHARGE and pay it himself, with no second person | High | Slice A |
| D4 | The "LATEST" FX fallback uses today's latest rate, so past profit drifts every time a new rate is set | Medium | Slice A |
| G3 | Back-dated cost changes restate past profit **and payables** without approval | Medium | Slice A (stop-gap), Slice C (freeze) |
| D2 | A partial online refund makes the whole capture disappear from profitability while `/finance` still counts it | Medium | Slice A |
| G5 | Unused settlement approvals never expire and cannot be cancelled | Medium | Slice A (expiry), Slice C (cancel) |
| D5 | A cancelled online booking still PAID (refund not yet done) shows 100 % margin, with no "refund due" flag | Medium | Slice B + owner Q6 |
| D6 | Office money summary is in EUR equivalents only, so EGP cannot be reconciled to the EGP cash box | Low | Slice B |
| G2 | Office refunds have no amount threshold and need no second person | Owner decision Q5 | later |
| G4 | All finance permissions are seeded to `platform-admin` only: four-eyes needs at least 2 real accounts and there are no role templates | Low (ops) | Slice B (docs/seed) |
| G6 | The approver can reverse his **own** adjustment (no not-own rule) | Low | Slice A |

**Recommended first slice: Slice A, Tier 1 (money rules and permissions), with no migration.** See §6.

---

## 1. SAF-04.01: EUR/EGP trace from collection to cash box

### 1.1 What is already sound (verified in code)
- **Collection** (`app/OfficeCollectionService.kt:137-223`): runs under the booking row lock and is capped at outstanding. EGP needs today's rate plus the quoted `fxRateId`, and stores `amount_paid` (native) plus `amount_eur`. Cash entries pass the day gate. Idempotency holds.
- **Partial payments**: `OfficePaymentSummary.of(totalEur, entries)` tracks outstanding. Overpayment is impossible.
- **Reversal** (`:239-314`): full-entry only and never by the person who recorded it. It cannot leave less collected than was refunded, in EUR (`:271`) and in EGP (`:276-289`). It is dated **today**, so it takes cash out of today's box (`:290-293`).
- **Refund** (`app/OfficeRefundService.kt:83-163`): only on CANCELLED office bookings, ≤ refundable, EGP refunded FIFO at the lot rates with any excess at today's rate (warning), never by the recorder's own reversal.
- **Cash box** (`infra/JooqFinanceOpsRepositories.kt:575-625`, `dom/FinanceOps.kt:520-535`): per Cairo day and **native currency**. Expected = cash collections − cash refunds − cash settlements, each net of reversals. Count and confirm are done by different users and expected is re-checked (`app/CashBoxService.kt:301-338`).
- **Currencies are never mixed** in payables (`CurrencyBalance` per currency) or in the cash box. Profitability converts everything to EUR and labels `rateSource`.

### 1.2 Defects (each needs a failing test first)

**D1: booked value vs collected vs recognised revenue are not separated (High).**
`infra/JooqFinanceOpsRepositories.kt:687` sets `officeRevenue = collected − refunded`. `dom/Profitability.kt:187` sets revenue = online + office collected. No booked value (`total_eur`) or outstanding is returned, and `erp/pages/profitability.vue:107-115` has no such column. The only label is "Office payments" (`erp/utils/financeOpsMessages.ts:63`).
*Scenario:* an office booking for 4 guests, total 200 EUR, with a 50 EUR deposit collected and cost 120 EUR. The departure row shows profit −70 EUR (−140 %) with **no flag**. When the balance is collected the next day it flips to +80. A month report taken before the end-of-day collections understates profit and looks reliable.

**D3: a missing supplier cost is not a gap (High).**
`dom/Profitability.kt:108-110` raises `NO_COST_COMPONENTS` only when the tour has *no* component at all. Payables does flag `NO_SUPPLIER_PRICE` for the same departure (`dom/Payables.kt`, `derive`), so the two reports disagree.
*Scenario:* tour X has an OWN "entry fee" of 100 EGP per person in force, and a SUPPLIER "boat" price of 600 EGP per person that was **ended** on 2026-10-31 with no successor entered yet. A 2026-11-02 departure with a supplier assigned shows cost = entry fee only, profit inflated, no badge. The same pattern applies when the hub import missed the supplier row for a tour.
*Fix:* add gap `SUPPLIER_COST_MISSING` when the departure has ≥1 assigned supplier and no SUPPLIER component (linked or unlinked) is in force. Optionally also add `DRIVER_NOT_ASSIGNED` for **past** departures with live bookings and no driver: today `:129` silently adds no driver cost.

**D4: the FX fallback drifts history (Medium).**
`app/ProfitabilityService.kt:69-73` falls back to `reads.latestRate()` (the global latest, `infra/...:816-822`). Rates can only be set for today (`app/FxRateService.kt:30`). So the profit of a past departure that had no rate on its day changes **every day a new rate is set**, with no cost edit at all.
*Scenario:* departure 2026-10-10 has EGP costs and no rate that day. On 10-11 it is costed at 52, on 10-20 at 55: the profit changes silently.
*Fix (no migration):* for past days use the nearest rate on or before the departure day (`DEPARTURE_DATE | PREVIOUS | LATEST(future only) | NONE`). That value is stable once set.

**D2: a partial online refund removes the whole capture from profitability (Medium).**
A partial refund moves the payment to `REVIEW_REQUIRED` (`app/HandlePaymobWebhookService.kt:150`). The code comment there says to keep the capture visible as revenue. But profitability counts only `STATUS = PAID` (`infra/...:669`), while `/finance` still counts the capture through `revenueRecognisedAt` (`erp/composables/useFinanceAggregation.ts:148-161`). The same booking therefore has revenue in `/finance` and 0 in `/profitability`, with no flag.
*Fix:* include `REVIEW_REQUIRED` with recognition in profit revenue and add a `PAYMENT_UNDER_REVIEW` flag that hides profit, the same way a cost gap does.

**D5: a cancelled online booking awaiting a Paymob refund (Medium, policy).**
`dom/Profitability.kt:104` treats a cancelled booking as "retained" revenue at zero cost. An online booking cancelled while its payment is still PAID shows 100 % margin until the refund webhook arrives. Whether that money is retained or due back depends on the cancellation policy (owner Q6).
*Fix:* add a `REFUND_PENDING` flag (cancelled + PAID + no refund) and exclude it from retained revenue until resolved.

**D6: the office summary is EUR-only (Low).**
`infra/...:777-801` sums `AMOUNT_EUR` only. EGP collected/refunded cannot be matched to the EGP cash box, and the summary is on a recorded-day basis while profitability is on a tour-day basis. Neither page states its date basis.
*Fix:* return native-currency subtotals and a `basis` field (`RECORDED_DAY` / `TOUR_DAY` / `PAYMENT_EVENT_DAY`) shown on each card.

**Note (not a defect, but it needs the reversal report in §3):** a reversal or refund of a collection from an already closed day lands in **today's** expected cash (by design, `OfficeCollectionService.kt:290`). Today's box then shows a variance that can only be explained by a cross-day list.

### 1.3 Recommended revenue vocabulary (needs owner Q1)
Per booking and per departure (tour-day basis, EUR with native subtotals):
- **Booked value** = `total_eur` of live bookings.
- **Collected** = recognised online + office collected net of reversals.
- **Refunded**, and **Outstanding** = booked − collected (live office bookings).
- **Refund due** = cancelled bookings still holding money that is due back.
- **Recognised revenue** (default proposal): booked value of live bookings on departures that have **run** (tour day ≤ today), plus money retained on cancelled bookings. Future departures show "booked", not "revenue".
- **Profit** = recognised revenue − cost, shown only with no cost gap; otherwise "—" with the gap flags.

Cash-basis profit stays available as a secondary column. This is a reporting choice, not an accounting standard, so it must be confirmed against how the owner actually runs the business.

---

## 2. SAF-04.03: freeze cost per departure (smallest safe design)

### 2.1 Problem
Profit (`ProfitCalculator.compute` over `costs.findAll()`) and payables (`PayablesCalculator.derive`) are **recomputed on every read** from dated components, the current slot assignment, current guests and (D4) the latest rate. `CostService.replace/end` (`app/CostService.kt:140`, `:171`) accept past dates; only the ERP warns. Ending or back-dating an agreement therefore silently restates past profit **and what is owed to a supplier**, possibly after it was paid. A change of driver, supplier or guests on a past slot does the same.

### 2.2 Design: an append-only "departure cost close" (no parallel ledger)
It follows the V33 cash-box pattern: events on an append-only table, the latest event wins, and lines are a snapshot of what the existing calculators produced. No journal and no debit/credit accounts are added.

**Migration V36 sketch** (V35 is the latest; re-check the number on the release branch):
```sql
-- events: CLOSE / REOPEN per departure (latest sequence wins, like cash_box_event)
CREATE TABLE wego.tours_operator_departure_cost_event (
  id uuid PRIMARY KEY,
  slot_id uuid NOT NULL REFERENCES wego.tours_operator_tour_slot(id) ON DELETE RESTRICT,
  service_date date NOT NULL,
  sequence int NOT NULL,
  kind varchar(8) NOT NULL CHECK (kind IN ('CLOSE','REOPEN')),
  egp_per_eur numeric(12,6), fx_rate_id uuid, rate_source varchar(16),   -- frozen on CLOSE
  guests int, reason varchar(500),
  actor_user_id uuid REFERENCES wego.identity_user(id) ON DELETE SET NULL,
  client_request_id uuid NOT NULL,
  occurred_at timestamptz NOT NULL,
  UNIQUE (slot_id, sequence), UNIQUE (actor_user_id, client_request_id),
  CHECK (kind <> 'REOPEN' OR reason IS NOT NULL),
  CHECK (kind <> 'CLOSE' OR rate_source IS NOT NULL)
);
-- frozen lines of one CLOSE: what is owed per party/currency, expected vs actual
CREATE TABLE wego.tours_operator_departure_cost_line (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES wego.tours_operator_departure_cost_event(id),
  source varchar(12) NOT NULL CHECK (source IN ('COMPONENT','DRIVER_RATE','ADJUSTMENT')),
  cost_component_id uuid, adjustment_id uuid,              -- provenance
  supplier_id uuid, driver_id uuid,                         -- NULL both = own cost
  category varchar(16) NOT NULL, label varchar(80) NOT NULL,
  currency char(3) NOT NULL, quantity int NOT NULL,
  expected_amount numeric(12,2) NOT NULL,                   -- from components
  actual_amount numeric(12,2) NOT NULL                      -- expected ± slot adjustments
);
-- frozen EUR cost allocated to each booking (so later guest edits cannot move it)
CREATE TABLE wego.tours_operator_departure_cost_booking (
  event_id uuid NOT NULL REFERENCES wego.tours_operator_departure_cost_event(id),
  booking_id uuid NOT NULL REFERENCES wego.tours_operator_booking(id),
  cost_eur numeric(12,2) NOT NULL,
  PRIMARY KEY (event_id, booking_id)
);
-- month close (owner)
CREATE TABLE wego.tours_operator_period_close_event (
  id uuid PRIMARY KEY, month date NOT NULL CHECK (extract(day FROM month) = 1),
  sequence int NOT NULL, kind varchar(8) NOT NULL CHECK (kind IN ('CLOSE','REOPEN')),
  reason varchar(500), actor_user_id uuid, client_request_id uuid NOT NULL,
  occurred_at timestamptz NOT NULL, UNIQUE (month, sequence)
);
-- append-only triggers identical to V33 (UPDATE/DELETE/TRUNCATE refused except FK user detach)
INSERT INTO wego.identity_permission ... ('tours-operator.cost:close', 'Close a departure''s cost (freeze)');
INSERT INTO wego.identity_role_permission ... ('platform-admin','tours-operator.cost:close');
```
Optionally add `settlement_approval_void` in the same migration (§3).

### 2.3 Rules
- **Close a departure** (`POST /finance/departures/{slotId}/cost-close`, permission `cost:close` for the manager):
  - The tour day must be ≤ today.
  - There must be no `CostGap` and no `OwedIssue` for its parties; otherwise 422 with the list.
  - It needs a rate under the D4 rule (`DEPARTURE_DATE` or `PREVIOUS`).
  - It runs under the slot lock plus the party locks, and snapshots the current calculator output. Actual differences are entered **before** closing as the existing slot-linked CHARGE/DEDUCTION adjustments.
- **While closed:**
  - Profitability uses the frozen `cost_eur` and rate and shows `costStatus=FROZEN`.
  - Payables use the frozen `actual_amount` lines for that slot instead of `derive`.
  - Slot-linked adjustments, assignment changes, and booking cancel or guest edits on that slot are refused with 409 `departure_cost_closed`.
  - Collections, refunds and unlinked adjustments are unaffected: they are already dated ledgers.
  - A cost-component change no longer touches closed departures. The response reports `affectedClosedDepartures` (count) for information.
- **Reopen** a departure (permission `settlement:approve`, owner; reason required): a later CLOSE writes a **new** snapshot, and the old lines stay as history (version diff visible).
- **Close a month** (`settlement:approve`): every departure in the month with live bookings must be closed. The action can bulk-close those without gaps and lists the rest. While a month is closed, a departure reopen in it is refused until the month is reopened (owner, with a reason).
- **Expected vs actual:** per party and currency, *variance = actual − expected* (the slot adjustments). Shown on the departure, in a "cost variance" report, and as a total per month. *Drift = live recompute − frozen expected* is a flag only ("agreement changed after close"); it never changes the figures.
- **Audit:** the event rows themselves (actor, time, reason, rate, provenance ids per line) plus the existing adjustment and component rows. The snapshot keeps each line's component and adjustment ids, so each frozen line traces back to its source rows.

### 2.4 Impact on existing reports and historical rows
- **The migration is additive:** 4 new tables and 1 permission. Nothing is backfilled and no existing row is rewritten. Rollback before first use = drop the tables.
- **Departures never closed behave exactly as today** (plus the Slice A fixes D3/D4, which can change some historical figures **once**, visibly: D3 turns some "reliable" profits into "—"; D4 moves LATEST-rate rows to the stable previous rate). Release notes must say so.
- An optional owner-triggered **"bulk close through date D"** freezes history at its current recompute. It must be run only **after SAF-04.02** (hub reconciliation and the 19 flagged cells), otherwise wrong costs get frozen.
- Payments already made are untouched. If a frozen actual is below what was paid, the existing negative-balance display (L4) applies.
- Wiring per the V33 precedent: both Gradle files, `release-profiles.json` `migrationVersions`, `ProductIsolationIntegrationTest`, regenerated release plans, jOOQ codegen.

### 2.5 Stop-gap until the freeze ships (part of Slice A)
`CostService.create/replace/end` with `validFrom < today` or `lastDay < today` require `settlement:approve` (403 `backdated_cost_needs_approval`) and return the count of affected past departures.

---

## 3. SAF-04.04: residuals

| Residual | Recommendation | Rationale |
|---|---|---|
| **Unused approval expiry** | **Do now** (Slice A, no migration) | Today a months-old approval can be consumed by any pay holder for the exact amount (`app/PayablesService.kt:208-216`). Add a check in `pay()`: `approvedAt + N days < now` → 409 `approval_expired` (default N = 7, owner Q3). The approval list shows expired ones. |
| **Approval cancellation** | **Later**, bundled with V36 | Needs an append-only `settlement_approval_void` row (approver or owner, with a reason). Expiry covers most of the risk until then. |
| **Reversal report** | **Do now** (Slice B, read-only, Tier 2) | Reversals of collections, refunds, settlement payments and adjustments are the main fraud and confusion path. Cross-day reversals already move today's cash box (§1.2 note). Spec: `GET /finance/reversals?from&to`: original entry (day, amount native and EUR, recorder), reversal (day, actor, reason), lag in days, cash flag, whether it crossed a closed cash day. Permission `payment:view`. |
| **F6: cumulative CHARGE cap per party per day** | **Do now** (Slice A, Tier 1) | See G1: the per-charge cap lets one person create unlimited ≤5000 charges and pay them. Rule: for non-approvers, today's (Cairo) non-reversed CHARGEs to the party + the new one must be ≤ 5000 EGP, otherwise 403 `charge_needs_approval`. **Move the check after `lockParty`** (today it is at `:282-287`, before the lock at `:288`), or concurrent charges race. Pair it with G1's second rule. |

---

## 4. SAF-04.05: report indicators

**Already available:**
- Online gross/refunded/net by payment-event day (`/finance`, EUR).
- Office collected/reversed/refunded by recorded day (EUR only).
- Profit by booking/departure/tour/month with cost-gap flags (tour day).
- Payables per party per currency, with open issues and pending approvals (`/settlements`).
- Cash box per day and currency with counted − expected at COUNT, plus recent days (latest event only).
- Bookings filtered by a single tour date (`GET /bookings?date=`), the Today view, and requests filtered by status.
- **No CSV/export exists** anywhere in the ERP.

**Missing** (each tour-day or recorded-day basis is stated explicitly; Cairo time; EUR with native subtotals unless noted):

| Indicator | Definition | Period / basis | Currency | Source | Permission |
|---|---|---|---|---|---|
| Requests & conversion | Requests created; converted; closed; open; conversion % = converted ÷ created (cohort by `created_at`); median hours to convert | Created day, range ≤ 366 d | n/a | `online_request` + `online_request_audit` (V34) | `booking:view` |
| Bookings by tour day | Per tour day: bookings, guests (adults/children), status mix, channel mix, live seats vs capacity | Tour day | n/a (counts) | `booking`, `tour_slot` | `booking:view` |
| Booked / collected / refunded / outstanding / refund due | §1.3 definitions, per tour day and totals | Tour day | EUR + EGP native | `booking`, `payment`, `office_collection`, `office_refund` | `payment:view` |
| Office money in native currency | The existing office summary split EUR/EGP native | Recorded day | Native | same | `payment:view` |
| Payables summary & aging | Per currency: total owed, overdue buckets by departure date (0-7/8-30/>30 d), open issues, pending/expired approvals | As of today | Per currency, never mixed | `PayablesService.overview` + approvals | `settlement:pay` / `approve` / `payment:view` |
| Cost gaps list | Departures with gaps (D3 included) or owed issues, with a link to fix | Tour day | n/a | `ProfitCalculator` + `PayablesCalculator` | `payment:view` or `cost:manage` |
| Cost variance (after §2) | Expected vs actual per departure/party; drift flags | Tour day | Native + EUR at frozen rate | `departure_cost_*` | `payment:view` |
| Cash-box variances | Every COUNT (not only the latest): expected, counted, difference; unclosed past days; reopen count and reasons | Business day | Per currency | `cash_box_event` | `cash-box:confirm` or `payment:view` |
| Reversals | §3 | Recorded day | Native + EUR | 4 ledgers | `payment:view` |

**Export rule:** CSV per report on the **same server permission** as the report, built server-side. Allowed columns: booking reference, tour, date, counts, amounts, flags. **Excluded:** customer name, phone, email, notes and staff emails, except the actor's display name on the reversal and cash reports. Keep `Cache-Control: no-store`, as finance responses already do.

---

## 5. SAF-04.06: server-side enforcement per finance action

| Action | Server permission (verified) | Separation-of-duties / limits on the server | Gap |
|---|---|---|---|
| Record office collection | `booking:collect-cash` | Booking lock, ≤ outstanding, cash-day gate | none |
| Reverse collection | `booking:reverse-collection` | Not own; refunds stay covered (EUR and EGP) | none |
| Record refund | `booking:refund-office` | Cancelled only, ≤ refundable, cash-day gate | **G2:** no amount threshold, no second person (owner Q5) |
| Reverse refund | `booking:refund-office` | Not own | none |
| Cost create/replace/end | `cost:manage` | Idempotency, duplicate check | **G3:** back-dating restates history and payables without approval |
| Pay supplier/driver | `settlement:pay` | ≤ balance under party lock; ≤ 5000 EGP/party/day, otherwise one exact approval; **approver ≠ payer** (`:214`); cash-day gate | **G5:** approval never expires |
| Approve payment | `settlement:approve` | ≤ balance | none |
| CHARGE/DEDUCTION | `settlement:pay` | CHARGE ≤ 5000 per charge for non-approvers; slot must be served by the party | **G1** (below) |
| Reverse payment | `settlement:approve` | Not own (F4) | none |
| Reverse adjustment | `settlement:approve` | — | **G6:** no not-own rule (`app/PayablesService.kt:318-347`) |
| Cash count / confirm / reopen | `cash-box:close` / `cash-box:confirm` | Confirmer ≠ counter; expected re-check; reopen needs a reason | none |
| Reports (profit, office, payables, cash, costs) | `payment:view` (+ the specific permissions) | Read-only, no-store | none |
| Print statement | `settlement:pay` or `settlement:approve` | Register STL numbering | none |

- **G1 (High), the self-dealing loop:** a user with `settlement:pay` (and no approve) can record CHARGE 4 999 EGP to a driver (the slot is optional; any past service date) and immediately pay it in cash. Repeating this across parties and days bypasses "approver ≠ payer" entirely, because no approval is ever needed. **Fix:** the F6 cumulative cap, plus "a user who recorded a non-reversed CHARGE for this party today cannot record a payment to it today" (403 `charge_recorder_cannot_pay`). Both are cheap, need no migration, and run under the party lock.
- **G4 (Low, ops):** V30/V33 grant every finance permission only to `platform-admin` (`V33...sql:434-440`). Four-eyes rules (L2, F4, count ≠ confirm) need ≥ 2 real accounts. Ship documented **role templates** via the existing role editor (`erp/pages/staff.vue`). Do not widen any permission:
  - *Reception*: `booking:view`, `booking:collect-cash`, `cash-box:close`.
  - *Manager*: Reception + `booking:reverse-collection`, `booking:refund-office`, `cash-box:confirm`, `settlement:pay`, `cost:manage`, `cost:close`, `payment:view`, `fx-rate:manage`.
  - *Owner*: all, including `settlement:approve`.
- Every row above needs an HTTP permission test (403 without the permission, plus each separation-of-duties rule). The OPS2-F matrix covers most of them; G1, G3, G5 and G6 need new tests.

---

## 6. Implementation slices

**Slice A, Tier 1 (do first; no migration; independent review plus concurrency tests):**
1. G1 + F6: cumulative daily CHARGE cap under the party lock, and the charge-recorder-cannot-pay-today rule.
2. G5: approval expiry (N days) in `pay()`.
3. G6: no reversing your own adjustment.
4. G3: back-dated cost changes need `settlement:approve`.
5. D3: `SUPPLIER_COST_MISSING` gap (optionally `DRIVER_NOT_ASSIGNED` for past departures).
6. D4: stable `PREVIOUS` rate for past days.
7. D2: `REVIEW_REQUIRED` handling and the `PAYMENT_UNDER_REVIEW` flag.

Tests: HTTP plus domain for each item, plus ERP labels EN/AR. Release note: figures for D3/D4 change once, visibly.

**Slice B, Tier 2 (read-only reports; after owner Q1):**
- §1.3 columns on profitability (booked/collected/outstanding/refund due) and D5.
- Native-currency office summary (D6).
- Reversal report, cash-variance history, cost-gaps list, payables aging, request funnel, bookings by tour day.
- CSV export under the same permissions; role-template doc.

**Slice C, Tier 1 (migration V36; after SAF-04.02):**
- Departure cost close/reopen, month close, approval void.
- Payables and profitability read frozen lines; variance report.

---

## 7. Open owner questions
1. **Revenue basis:** should profit use *booked value for departures that ran* (proposed) or *cash collected* (today)? Should both be shown?
2. **Who closes departures, and when:** the manager the day after the tour? A month close by the owner on which day?
3. **Approval expiry:** 7 days?
4. **Charge cap:** the same 5 000 EGP per party per day as payments?
5. **Refunds:** should office refunds above a threshold (e.g. 5 000 EGP-equivalent) need owner approval and a second person?
6. **Cancelled online bookings:** is the money retained or refunded through Paymob? This decides D5.
7. **Accounts:** how many real staff accounts, and in which roles (Reception / Manager / Owner)? Four-eyes needs at least 2.
8. **Production state:** does it already hold real finance rows, and was `import_costs.py --apply` run? This decides the D3/D4 one-time restatement note and any bulk close.
