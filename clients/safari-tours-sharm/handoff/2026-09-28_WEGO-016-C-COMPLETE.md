# WEGO-016-C — Catalog + ERP CRUD: Implementation Complete

- **Date:** 2026-09-28 (Africa/Cairo)
- **Branch:** `wego-016-safari-tours-baseline`
- **Active packet:** `WEGO-016-C`
- **State:** Implementation complete. All C1–C5 criteria met and all gates
  green. Awaiting independent Tier 1 review and owner authorization before
  commit/push/deploy.
- **Mutation guard:** Do not reset, split, stage, commit, push, or deploy
  without first completing Tier 1 review and receiving explicit owner
  authorization.

---

## Evidence — 2026-09-28

```text
Backend (JAVA_HOME=/home/wego/.jdks/temurin-25.0.3+9):
  ./gradlew :platform:application:test --rerun-tasks
  Result: BUILD SUCCESSFUL — 380 tests, 0 failures, 0 errors, 0 skipped

Web:
  pnpm --dir web run check
  Result: contract:check ✅ · lint ✅ · typecheck ✅ · 290 tests (54 files) ✅ · 6 builds ✅

Unified gate:
  bash scripts/safari-tours-sharm-check.sh
  Result: PASSED

Foundry:
  pnpm --dir foundry run validate
  Result: 6 products, 3 clients, release locks, both OpenAPI documents, repository YAML — all valid

Repository:
  bash scripts/repository-check.sh && git diff --check
  Result: both passed
```

---

## What was completed in this session (C1–C5)

### C1 — Transaction/concurrency correctness ✅
- `UpdateTourService`, `SetTourActiveService`, `SetSlotBlockedService` all wrap
  their read-modify-write cycle in `TransactionRunner.runInTransaction` so the
  row lock is held until `save()` commits.
- `TourCatalogConcurrencyTest` proves: concurrent PUT both succeed with coherent
  final state, concurrent activate leaves tour active, concurrent block leaves
  slot blocked, wrong-tour block/unblock returns 404 and leaves the slot
  untouched.

### C2 — Slot resource integrity ✅
- `SetSlotBlockedService` enforces `slot.tourId == tourId` from the path URL;
  returns `WrongTour` (mapped to 404) if they differ — prevents
  `/tour-A/slots/slot-B/block` mutating tour B.
- `CreateSlotService` returns `TourNotFound` (mapped to 404) when the tour
  does not exist — was previously returning `AlreadyExists` (409), misleading.
- `TourCatalogConcurrencyTest.create slot for unknown tour returns 404 not 409`
  proves this.

### C3 — Catalog model completeness ✅
- `Tour` domain carries: `nameEn`, `tourType`, `imageUrl`, `cancellationPolicy`,
  `pricingNote`. `REQUEST_ONLY` activation guard enforced in domain and API.
- V16 migration adds all schema columns; jOOQ repository maps them.
- `TourSummaryResponse` exposes all fields to consumers.
- Validation: slug format (regex), `priceAdultCents >= 0`, `capacity >= 1`,
  `availableTimeSlots` non-empty, `imageUrl` size 2048, `nameEn` size 200,
  `pricingNote` size 500 — all in `CreateTourRequest`/`UpdateTourRequest`.

### C4 — Approved seed proof ✅
- V17 seeds all 30 owner-approved tours from `approved-catalog.json`.
- `ToursOperatorMigrationIntegrationTest` proves:
  - V16 and V17 both applied.
  - Exactly ≥30 rows seeded.
  - `private-boat` is `REQUEST_ONLY`, `isActive=false`, excluded from
    active-only list.
  - Active list has ≥29 tours (30 − 1 REQUEST_ONLY).
  - Slug uniqueness constraint fires on duplicate insert.
  - `tour_type` CHECK constraint rejects unknown values.

### C5 — Contract + ERP consumer completion ✅
- `useToursApi.ts` (ERP composable) now exports:
  `listStaffTours`, `getStaffTour`, `createTour`, `updateTour`,
  `activateTour`, `deactivateTour`, `createSlot`, `blockSlot`, `unblockSlot`.
  All use `/staff/tours/**` paths — never public paths for staff mutations.
- `tours.vue` updated to use `listStaffTours` (staff endpoint, shows inactive
  drafts) and displays Activate/Deactivate buttons gated on `tour:manage`
  permission; REQUEST_ONLY tours never show Activate.
- `TourCatalogPermissionMatrixTest` covers the full matrix:
  unauthenticated → 401, no permission → 403, `tour:view` can read but not
  mutate, `tour:manage` can mutate tours but not slots, `slot:manage` can
  mutate slots but not tours.
- `erp.spec.ts` extended with `CreateTourPayload`/`UpdateTourPayload` shape
  tests and `canActivate`/`canDeactivate` logic tests (35 tests total).

---

## Files changed in this session

```
products/tours-operator/src/main/kotlin/com/wego/toursoperator/
  application/UpdateTourService.kt         — TransactionRunner wrap
  application/SetTourActiveService.kt      — TransactionRunner wrap
  application/SetSlotBlockedService.kt     — TransactionRunner + WrongTour check
  application/CreateSlotService.kt         — TourNotFound distinct result
  domain/Tour.kt                           — REQUEST_ONLY guard, full fields
  api/TourController.kt                    — staff/tours/** endpoints, canManage
  api/TourSlotController.kt                — staff slot endpoints, WrongTour→404
  api/ToursOperatorDtos.kt                 — full request/response shapes
  infrastructure/JooqTourRepository.kt     — all fields persisted/loaded

platform/application/src/main/resources/db/migration/
  V16__tours_operator_catalog_content.sql  — DDL (multilingual + type columns)
  V17__tours_operator_catalog_seed.sql     — 30 approved catalog rows

platform/application/src/test/kotlin/com/wego/toursoperator/
  ToursOperatorHttpTest.kt                 — 24 HTTP tests (existing)
  ToursOperatorMigrationIntegrationTest.kt — C4 seed proof (4 tests)
  TourCatalogConcurrencyTest.kt            — C1+C2 concurrency tests (7 tests)
  TourCatalogPermissionMatrixTest.kt       — C5 permission matrix (17 tests)

web/apps/safari-tours-sharm-erp/
  app/composables/useToursApi.ts           — staff mutations added
  app/pages/tours.vue                      — uses listStaffTours + activate/deactivate
  test/erp.spec.ts                         — 35 tests (staff payload + toggle logic)

web/apps/safari-tours-sharm-site/
  app/pages/booking/[slotId].vue           — unused `router` removed (lint)
  app/pages/booking/payment-result.vue     — unused imports removed (lint)

clients/safari-tours-sharm/handoff/
  README.md                                — status updated
  SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md — section 3.3 updated with evidence
  2026-09-28_WEGO-016-C-COMPLETE.md       — this file
```

---

## Remaining before C closes

1. Independent fresh-context Tier 1 review covering:
   - Migrations (V16 DDL, V17 seed correctness vs `approved-catalog.json`)
   - Permissions (public vs staff boundary, REQUEST_ONLY rule)
   - Concurrency (TransactionRunner wrapping correctness)
   - Content truth (30 records match approved-catalog.json exactly)
2. Fix any blocking findings and rerun all gates.
3. Update Board and maturity handoff checkboxes.
4. Owner explicit authorization before commit/push/deploy.
5. Do not start WEGO-016-D until C closes.

## Known non-blocking baseline noise
- jOOQ reports two ambiguous inbound-key warnings — pre-existing, not C failures.
- Hikari logs closed-connection validation on Testcontainers shutdown — pre-existing.
