# WEGO-016-C — Continue production catalog after controller/security repair

- **Date:** 2026-09-28 (Africa/Cairo)
- **Branch:** `wego-016-safari-tours-baseline`
- **Active packet:** `WEGO-016-C` only
- **State:** controller/API blocker repaired and focused HTTP suite green;
  packet C is not complete and still requires full implementation, full gates,
  and independent Tier 1 review.
- **Mutation guard:** preserve every current modified and untracked file. Do not
  reset, split, stage, commit, push, or deploy without first inventorying the
  worktree. C has no commit/push/deploy authorization yet.

## Start here

1. Read `AGENTS.md`, `docs/ENGINEERING_CONSTITUTION.md`, and the complete
   `WEGO-016-C` section in `docs/execution/WEGO_EXECUTION_BOARD.md`.
2. Read `SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md` and this file.
3. Run `git status --short`; C currently contains V16/V17, catalog domain,
   repositories/services/controllers/tests/OpenAPI, and generated web contract
   changes. Treat them as one in-progress packet.
4. Do not redo A or B. B is complete, reviewed, and committed as `ae09026`.

## Blocker repaired in this session

The first focused run had **23 tests, 7 failures**. The failures exposed real
contract and security defects, not flaky assertions:

1. `POST /tours/staff` matched the public `/tours/*` security rule. An
   unauthenticated request reached the controller instead of returning 401.
2. Staff CRUD had authentication only and no `tour:manage`/`slot:manage`
   method authorization.
3. `TourController` requested `UserDetails`, but the actual principal is
   `AuthenticatedUser`; `createdByUserId` silently became null.
4. Public `activeOnly=false`, by-id, and by-slug reads could expose inactive
   drafts, including `REQUEST_ONLY` catalog records.
5. OpenAPI advertised staff mutations on public paths and used an undeclared
   security scheme named `bearer` instead of `bearerAuth`.
6. Kotlin/Jackson serialized boolean properties as `active`/`blocked` while
   OpenAPI and generated consumers require `isActive`/`isBlocked`.
7. Optional enum fields were not safely deserializable when omitted, and the
   slot block test queried a date different from the seeded slot date.

### Fix now present

- Public catalog remains under `/api/v1/tours-operator/tours/**` and always
  hides inactive tours.
- Staff catalog is isolated under `/api/v1/tours-operator/staff/tours/**`.
- Staff reads require `tours-operator.tour:view`; mutations require
  `tours-operator.tour:manage`; slot mutations require
  `tours-operator.slot:manage`.
- Create uses `(authentication.principal as AuthenticatedUser).userId`, and the
  HTTP test proves the real actor UUID is persisted.
- Newly created inactive drafts return 404 publicly but remain readable through
  the permissioned staff endpoint.
- `isActive` and `isBlocked` are fixed explicitly with JSON property names.
- OpenAPI paths/security match controllers; generated TypeScript was refreshed.
- Execution Board now has exactly one `ACTIVE` packet: C.

## Evidence reproduced after the fix

```text
JAVA_HOME=/home/wego/.jdks/temurin-25.0.3+9 \
  ./gradlew :platform:application:test \
  --tests com.wego.toursoperator.ToursOperatorHttpTest --rerun-tasks

Result: BUILD SUCCESSFUL — 24/24 focused HTTP tests passed.

pnpm --dir web run contract:check
Result: generated web API contract matches OpenAPI.

pnpm --dir foundry run validate
Result: 6 products, 3 clients, release locks, both OpenAPI documents,
repository YAML — all valid.

bash scripts/repository-check.sh
git diff --check
Result: both passed.
```

The existing two jOOQ ambiguous inbound-key warnings remain non-blocking
baseline noise; do not misreport them as C test failures.

## Required continuation — in order

### C1 — Transaction/concurrency correctness (BLOCKING)

- `UpdateTourService`, `SetTourActiveService`, and `SetSlotBlockedService` call
  `findByIdForUpdate()` and `save()` in separate repository transactions. The
  row lock is released when the repository read returns, so it does not protect
  the following write. Wrap each complete read-modify-write operation in the
  product `TransactionRunner` (same pattern as booking services), then add real
  concurrency tests that fail on the pre-fix implementation.
- Review `CreateSlotService` duplicate checking for a concurrent insert race;
  keep the DB unique constraint as the final backstop and map the collision to
  a clean 409.

### C2 — Slot resource integrity (BLOCKING)

- Block/unblock currently accepts both `tourId` and `slotId` but the service
  uses only `slotId`. Reject a slot that does not belong to the path's tour;
  never allow `/tour-A/slots/slot-B/block` to mutate tour B.
- Return a distinct `TourNotFound` result for slot creation instead of reusing
  `AlreadyExists` and returning a misleading 409.
- Validate future date, allowed date range, and capacity policy. Add 404/400,
  wrong-tour, duplicate, and past-date HTTP tests.
- Public slot reads must not expose availability for an inactive tour.

### C3 — Catalog model and persistence completeness

- V16 adds `name_ar`, `name_ru`, `name_it`, `description_en`, `short_desc_en`,
  and `pricing_note`, but the current domain/API/repository only carries
  `nameEn`, type, image, and policy. Either implement every field accepted by
  C's criteria or explicitly narrow V16 before review; do not leave dead schema
  presented as a complete multilingual catalog.
- Add validation for image URL, text lengths, `REQUEST_ONLY` activation rules,
  and zero-price semantics. A request-only tour must never enter paid booking.
- Add an append-only audit/revision record for price, policy, activation, and
  capacity changes as required by the maturity handoff.

### C4 — Approved seed proof

- Prove `approved-catalog.json` and V17 are deterministically identical for all
  30 records: slug, type, active state, price, category, capacity, schedule,
  image, and cancellation policy.
- Add migration/integration assertions for 30 catalog rows and specifically
  prove Private Boat is `REQUEST_ONLY`, inactive, and excluded from public
  list/by-id/by-slug endpoints.
- Do not silently invent missing child prices, translations, schedules, rights,
  or policies. Record any mismatch as an explicit blocker.

### C5 — Contract and ERP consumer completion

- Keep OpenAPI as the source of truth and regenerate
  `web/packages/api-contract/src/generated.ts` after every contract change.
- Add ERP API methods/screens for staff list, create, update, activate,
  deactivate, create slot, and block/unblock only to the extent authorized by
  C. Use `/staff/tours/**`; never call staff writes through public paths.
- Add permission tests: unauthenticated 401, authenticated without permission
  403, view-only cannot mutate, manage can mutate. The current session added
  the no-permission create proof; complete the matrix.

### C6 — Closure gates

1. Run focused domain/service/HTTP/concurrency tests while implementing.
2. Run the full backend suite, not only `ToursOperatorHttpTest`.
3. Run `pnpm --dir web run check` and all production builds.
4. Run `bash scripts/safari-tours-sharm-check.sh`.
5. Update the Board and maturity checkboxes with exact counts/evidence.
6. Trigger an independent fresh-context Tier 1 review for migrations,
   permissions, content truth, and concurrency; repeat after fixes until zero
   blocking findings.
7. Do not start D, commit, push, or deploy unless the Board gate is closed and
   the owner explicitly authorizes the action.

## Files central to continuation

- `products/tours-operator/src/main/kotlin/com/wego/toursoperator/api/TourController.kt`
- `products/tours-operator/src/main/kotlin/com/wego/toursoperator/api/TourSlotController.kt`
- `products/tours-operator/src/main/kotlin/com/wego/toursoperator/api/ToursOperatorDtos.kt`
- `products/tours-operator/src/main/kotlin/com/wego/toursoperator/application/`
- `products/tours-operator/src/main/kotlin/com/wego/toursoperator/infrastructure/JooqTourRepository.kt`
- `platform/application/src/main/resources/db/migration/V16__tours_operator_catalog_content.sql`
- `platform/application/src/main/resources/db/migration/V17__tours_operator_catalog_seed.sql`
- `clients/safari-tours-sharm/content-research/approved-catalog.json`
- `platform/application/src/test/kotlin/com/wego/toursoperator/ToursOperatorHttpTest.kt`
- `platform/contracts/openapi/v1/wego-api.yaml`
- `web/packages/api-contract/src/generated.ts`

