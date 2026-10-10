# Request intake control — implementation and review evidence

Status: implemented and independently reviewed locally, 2026-10-07.
No commit, push, deployment or production access. The current worktree is
`wego-010a-0r-isolation`; the existing documentation corrections are preserved.

## Behavior

- `/sales` in the ERP requires `travel-sales:manage`. V9 grants it initially
  only to platform-admin. Staff can pause/resume with an optional private
  reason; each saved change records version, actor and time transactionally.
- Public status returns only `requestsOpen`, with `Cache-Control: no-store`.
  Public WEBSITE/MOBILE creates return `503 requests_paused` during a pause.
- Request creation takes a shared lock on the singleton row; changes take an
  exclusive lock. When a pause succeeds, earlier creates have finished and no
  new create can commit until resume. Rejected creates produce no request,
  request audit or notification. Old manager versions return 409.
- Existing idempotency keys can recover an already-created request during a
  pause. Catalog, tracking and existing staff lifecycle operations continue.
- Website and ERP show EN/AR notices. A fresh submit requires known-open
  status; network failures are not interpreted as open. Form details remain.
  A lost-response retry retains its original idempotency key.

## Verification actually performed

`bash scripts/sharm-to-go-check.sh` passed on the final implementation:
backend checks, mobile checks, both frontends' lint/typecheck/tests/build,
Foundry/OpenAPI validation, repository invariants and `git diff --check`.
The site has 101 passing tests and ERP has 87. Mobile checks were cached
where unchanged. Full log: `/tmp/stg-sales-quality-gate.log`.

Repository `SalesControlHttpTest` has eight real PostgreSQL integration
cases covering permission denial, request side-effect rejection, replay and
existing operations, validation/stale edits, concurrent cutoff in both lock
orders, rollback, and V8-to-V9 upgrade preserving existing data.

An independent fresh-context reviewer (`/root/sales_review`, required by
`docs/operations/AGENT_COLLABORATION.md`) found one NON-BLOCKING UI bug:
an older availability GET could overwrite a later `requests_paused` POST
rejection. `useSalesStatus.markPaused()` now invalidates those reads. The
same independent probe failed before the fix and passed afterward; a
permanent regression test was added. Final verdict: zero open BLOCKING or
NON-BLOCKING findings.

Independent executable results: 11/11 PostgreSQL adversarial cases,
10/10 UI race/replay cases, site 101/101 and ERP 87/87 with lint/typecheck.
Additional PostgreSQL probes covered simultaneous manager edits, an
idempotent winner committing while a replay waits behind pause, and a
database-trigger audit failure rolling back the state change. Probe source
and results are temporary artifacts, not repository test-suite additions:
`/tmp/stg-sales-review-probes/`, `/tmp/stg-sales-review.init.gradle`,
`/tmp/stg-sales-review-backend-probes.log`,
`/tmp/stg-sales-review-backend-probes.xml`,
`/tmp/stg-sales-review-ui-probes-after.log`.

The real production builds were exercised through a local Nginx copy of the
client configuration, an isolated PostgreSQL 18.4 fixture and the actual
backend jar. CUA-controlled Chrome verified: an open form reaches review;
ERP pause saves and displays the actual flag; the Arabic global notice and
disabled submit appear without losing date/party; private reason is absent
from the public page; ERP resume plus status refresh enables the same form.
The Arabic screen was visually inspected. This is local browser evidence,
not launch UAT. Only a synthetic test service was published in this fixture.

The actual catalog importer created 41 services with zero failures, and its
immediate repeat skipped 41 with zero creates. All real catalog services
remain APPROVED, unpublished; this does not prove interrupted imports safe.

## Remaining limits

Managed photos/rights approval are not implemented for Sharm To Go. The
advanced Safari worktree does contain a useful managed-media reference;
the older claim that it lacked uploads was corrected. Owner photos, SMTP,
mobile API wiring, translations, mobile performance and launch operations
retain their roadmap status. This slice adds no payments, financial module,
document center or fixed-slot inventory model.

## Subsequent mobile spacing correction (Tier 2)

The Arabic review form at 390×844 showed the fixed WhatsApp contact overlapping
the bottom 10.8px of the submit area at page end. The request main now reserves
96px bottom padding on small screens, returning to its original 32px from
the sm breakpoint. CUA read-only DOM geometry verified overlap=true before,
overlap=false after; submit bottom 722.8px, WhatsApp top 776px. Screenshot
inspection confirmed clear controls, and content width 375px stayed within
the 390px viewport. The viewport override was reset afterward.

Site lint/typecheck, 101 tests and production build passed after this class-only
change (`/tmp/stg-mobile-clearance-check.log`). No request/business behavior
changed after the independent Tier 1 review. This is a focused responsive check,
not a claim of full device UAT or improved Lighthouse performance.
