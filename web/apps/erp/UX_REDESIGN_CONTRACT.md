# WEGO-014 Phase 1 — UX and regression contract

This is the frozen baseline for the ERP redesign: what a later phase may
change freely (visual structure, markup, components) versus what it must
not break without a deliberate, documented replacement recorded in the
execution board. Built by direct inspection of `web/apps/erp/app/pages/`
and `e2e/tests/erp-lifecycle.spec.ts` — every count below is grep output,
not an estimate.

## Route inventory (17 routes)

| Route | Archetype | Permissions checked |
|---|---|---|
| `/login` | Authentication | none (public) |
| `/` (index) | Dashboard | `booking:view`, `diver:view`, `equipment:view`, `offering:view` |
| `/offerings` | Directory + create form | `offering:view`, `offering:manage`, `boat-charter:view`, `boat-charter:manage` |
| `/bookings` | Directory + create form | `booking:view`, `offering:view`, `booking:create`, `booking:cancel`, `booking:payment-update`, `booking:refund` |
| `/divers` | Directory + long form | `diver:view`, `diver:manage` |
| `/equipment` | Directory + workflow | `equipment:view`, `equipment:manage` |
| `/boat-charters` | Directory + create form | `boat-charter:view`, `boat-charter:manage` |
| `/course-enrollments` | Directory + workflow | `course:view`, `course:manage` |
| `/accounts` | Directory + admin form | `identity:user-view`, `identity:user-manage` |
| `/roles` | Directory + admin form | `identity:role-view`, `identity:role-manage` |
| `/employees` | Directory + long form | `hr:employee-view`, `hr:employee-manage` |
| `/attendance` | Directory + correction form | `hr:attendance-view`, `hr:attendance-manage` |
| `/leave-requests` | Workflow queue | `hr:leave-view`, `hr:leave-manage` |
| `/chart-of-accounts` | Directory + create form | `accounting:coa-view`, `accounting:coa-manage` |
| `/journal-entries` | Directory + line-editor form | `accounting:journal-view`, `accounting:journal-manage` |
| `/payroll` | Workflow (draft → post) | `payroll:view`, `payroll:manage` |
| `/reports` | Financial report (3 sub-reports) | `accounting:journal-view` |

Permission-visibility conditions (which controls a role can see) are frozen
as-is. A redesign may restructure *how* a gated control is shown, never
*whether* it's shown for a given permission set.

## Frozen selector contract

Every accessible name / element id below is depended on by the real,
passing test suite (113 ERP Vitest cases, 6 real nginx-fronted E2E
lifecycle tests, 38 steps). A migration touching a page that uses one of
these must either keep the name/id stable, or replace it and update the
dependent test *in the same change* — never leave a selector to rot.

### `getByRole` names (23 total — buttons unless noted)

Approve, Cancel, Create account, Create booking, Create draft, Create
employee, Create offering, Discard, Edit, Mark paid, Next, Post, Post
entry, Previous, Record attendance, Refund, Reverse, Run, Sign in, Sign
out, Submit request, Terminate, **Offerings** (the one `link`, not
`button` — see the login-panel fix in WEGO-013/`07d7a10`; the dashboard
also exposes an `Offerings` link and must keep doing so once the login
panel's shortcut links are eventually retired in favor of one real nav).

### `#id` field selectors (36 total)

`#accountType #amount #attendanceDate #balanceSheetDate #code
#currencyCode #customerEmail #customerName #description #email
#employeeId #endDate #entryDate #fullName #hireDate #incomeFrom #incomeTo
#line-account-0 #line-account-1 #line-amount-0 #line-amount-1
#line-direction-1 #name #notes #offeringId #partySize #password
#payPeriodEnd #payPeriodStart #position #reason #startDate #startsOn
#status #title #trialBalanceDate`

### High-risk patterns — will break on first contact with real tables/dialogs

- **9 `locator("li", { hasText: ... })` record locators** (bookings,
  employees, leave-requests, journal-entries ×2, payroll ×3). These
  assume each record renders as a single `<li>`. Phase 5-7 (whichever
  migrates the owning page) MUST replace each with a named
  row/region/`data-testid` locator in the *same* change that introduces
  a real table/list component — never left dangling against markup that
  no longer exists.
- **3 `runButtons.nth(0/1/2)` selections** in the Reports page test —
  fragile against any reordering of the three report sections. Phase 7
  should give each "Run" button a distinguishing accessible name (e.g.
  "Run trial balance") instead of relying on DOM order, and update the
  test to match by name, not position.
- **10 `window.confirm` + 1 `window.prompt` + 1 `window.alert` calls**
  across the app. Vitest currently mocks these directly. Any page that
  moves a confirmation into a real `WegoDialog` (Phase 3+) must update
  that page's own test to interact with the dialog, not the browser
  native mock — tracked per-page, not swept in bulk.
- **29 generic `wrapper.get("form")` submissions** in the Vitest suite —
  safe today because each page has exactly one `<form>`. A page gaining a
  second form (e.g. a dialog with its own form) breaks this ambiguously;
  each such page's test needs a scoped selector the moment that happens.

## Validated widths

390px (mobile), 768px (tablet), 1440px (desktop), plus 200% browser zoom.
Phase 8 exercises all 17 routes at all three widths.

## A rate-limiter constraint carried over from WEGO-013

The nginx edge login limiter is tuned for `burst=6` — the E2E suite's
existing 6 sequential lifecycle logins fit exactly. Phase 8's multi-width
sweep must reuse one authenticated context/session rather than logging in
again per viewport, or it will re-trip the same limiter WEGO-013 just
fixed. The limiter threshold itself is not to be touched again for this
packet's convenience.

## What Phase 1 does NOT include

No visual changes in this phase. No component work. This document is the
baseline every later phase is checked against, not a design spec.
