# WEGO-016-B — Fresh-context Tier 1 review handoff

- **Date:** 2026-09-27 (Africa/Cairo)
- **Packet:** `WEGO-016-B`
- **State:** implementation complete; independent review pending
- **Branch:** `wego-016-safari-tours-baseline`
- **Mutation guard:** preserve every staged, modified, and untracked file; this
  worktree contains the uncommitted Safari baseline. Do not commit, push,
  deploy, or activate `WEGO-016-C` without the required review outcome and
  explicit owner authorization where applicable.

## Start here

1. Read `AGENTS.md`, `docs/ENGINEERING_CONSTITUTION.md`,
   `docs/operations/REVIEW_INTENSITY.md`, and
   `docs/operations/AGENT_COLLABORATION.md` completely.
2. Read the `WEGO-016-B` section in
   `docs/execution/WEGO_EXECUTION_BOARD.md` and the current maturity handoff.
3. Confirm the worktree inventory with `git status --short`; do not normalize or
   discard changes that predate this review.
4. Review from executable behavior and generated-contract evidence, not from
   the implementation summary below.

## Outcome implemented

- `platform/contracts/openapi/v1/wego-api.yaml` contains every current
  tours-operator route with honest public/bearer security and DTO schemas.
- Backend, website, and staff ERP use one Money representation:
  `{ amount: decimal-string, currencyCode: ISO-4217 }`.
- Booking input/output use one nested `customer` object.
- `web/packages/api-contract/src/generated.ts` is generated from OpenAPI;
  `scripts/check-web-api-contract.sh` fails on drift.
- Web money arithmetic uses integer minor units/`bigint`, including totals,
  averages, and customer/finance aggregation. No Safari `parseFloat` money
  path or duplicate handwritten response interface should remain.
- Tour slug lookup is `/api/v1/tours-operator/tours/by-slug?slug=...`, avoiding
  ambiguous path templates and client-side download/filter.
- Checkout now receives slot context. The just-created booking crosses the
  confirmation redirect through `sessionStorage`; the phone number is not put
  into the URL.

## Adversarial checks required

1. Compare every OpenAPI tours-operator path/schema/security declaration with
   controller mapping, serialized JSON, Bean Validation, and exception shape.
2. Exercise public versus staff authorization boundaries, including the shared
   `/bookings` path (`POST` public, `GET` protected) and lookup PII behavior.
3. Attempt malformed, oversized, unknown-field, null, mixed-currency, and
   boundary Money/customer payloads. Separate a B contract defect from known
   later hardening (`idempotency`, `rate limiting/enumeration`, request-size and
   date-range limits), but record any release-blocking interaction honestly.
4. Verify that both frontends import generated contract types and cannot drift
   silently; deliberately perturb generation in a throwaway way if needed,
   then restore only the reviewer's temporary change.
5. Verify price display and aggregation do not coerce decimal money through a
   JavaScript binary float. Check zero children, missing child price, large
   amounts, mixed currency, average rounding, and confirmation reload.
6. Re-run real HTTP integration evidence with Testcontainers and at least the
   focused frontend contract tests before accepting the provided full-gate log.
7. Report each finding as `file:line`, severity (`BLOCKING` or
   `NON-BLOCKING`), defect, and concrete trigger. Append review rounds to the
   Board; do not erase this implementation record.

## Evidence already reproduced by the implementer/reviewer

```text
bash scripts/safari-tours-sharm-check.sh
  Backend: 332 tests, 0 failures, 0 errors, 0 skipped
  pnpm install --frozen-lockfile: passed
  Web: lint + typecheck + 414 tests + 6 production builds: passed
  OpenAPI/Foundry/release locks/repository YAML: passed
  Repository structure, legacy-snapshot quarantine/digest, diff check: passed

JAVA_HOME=/home/wego/.jdks/temurin-25.0.3+9 \
  ./gradlew :platform:application:test \
  --tests com.wego.toursoperator.ToursOperatorHttpTest --rerun-tasks
  Result: passed
```

Existing non-blocking test/build noise: jOOQ reports two ambiguous inbound key
names; Hikari logs closed-connection validation while Testcontainers contexts
shut down. Neither produced a failure or skip. Treat a new correctness impact
as a finding, but do not misreport the current warnings as failed evidence.

## Files central to the review

- `platform/contracts/openapi/v1/wego-api.yaml`
- `products/tours-operator/src/main/kotlin/com/wego/toursoperator/api/`
- `platform/application/src/test/kotlin/com/wego/toursoperator/ToursOperatorHttpTest.kt`
- `web/packages/api-contract/`
- `scripts/check-web-api-contract.sh`
- `web/apps/safari-tours-sharm-site/app/composables/usePublicToursApi.ts`
- `web/apps/safari-tours-sharm-site/app/pages/tour/[slug].vue`
- `web/apps/safari-tours-sharm-site/app/pages/booking/[slotId].vue`
- `web/apps/safari-tours-sharm-site/test/public-api.spec.ts`
- `web/apps/safari-tours-sharm-erp/app/composables/useToursApi.ts`
- Safari ERP finance/customer/booking/tour pages and `test/erp.spec.ts`

## Closure rule

`WEGO-016-B` closes only after a fresh-context independent reviewer records
zero blocking findings after all fixes and reruns the proportionate gates.
Then update the Board and maturity handoff. `WEGO-016-C` is the next planned
packet, not currently active; its migration/permissions/content work requires
new Tier 1 scope and verified owner commercial inputs.
