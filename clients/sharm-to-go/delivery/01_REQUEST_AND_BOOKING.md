# Phase 1 — request and booking foundation

## Dependency gate

- [x] Phase 0 session-safety checks are complete (see `ROADMAP_AR.md` Phase 0).
- [x] Current public catalog contract and DB migrations have been inspected
  (V1-V4; `ServiceRepository`/`CategoryRepository`/`Service`/`ServiceOption`
  read directly, not re-derived).
- [x] Review tier is classified: Tier 1 (new migration, public write, PII) —
  see the 2026-09-30 WEGO-010-A Phase 1A board entry.

## Contract and domain — sub-packet 1A, done 2026-09-30

- [x] Canonical aggregate name: `TravelRequest`
  (`products/travel-marketplace/.../domain/TravelRequest.kt`).
- [x] Public reference format: `STG-XXXXXXXX`, 8 chars from a 32-symbol
  unambiguous alphabet via `SecureRandom` — not sequential, not guessable, no
  PII (`TravelRequestReference.kt`).
- [x] States: `NEW`, `IN_REVIEW`, `CONFIRMED`, `COMPLETED`, `CANCELLED`,
  `EXPIRED` (`TravelRequestStatus.kt`).
- [x] Guarded transition table (see `TravelRequest.kt`'s class doc), actor
  requirements (`TravelRequestActorType`: CUSTOMER/STAFF/SYSTEM, enforced at
  both the Kotlin command boundary and a DB CHECK constraint that a STAFF
  actor is always attributed to a real user), typed cancel reasons
  (`TravelRequestCancelReason`).
- [x] Snapshot service id/name, option, price/currency/basis and cancellation
  policy at request creation, read from the catalog once and never re-read
  live (`CreateTravelRequestService`).
- [x] Store requested date/time, adults, children, hotel/pickup, locale,
  notes and source channel (`TravelRequest` fields; `V5` schema).
- [x] Require customer name and at least one valid reachable contact
  (`TravelRequestCustomer`'s init invariant, mirrored by a DB CHECK).
- [~] Consent/retention/redaction policy for customer PII: not yet written as
  a standalone policy document — deferred to the "Privacy, legal and support"
  work already tracked in `06_LAUNCH_AND_OPERATIONS.md`; the schema itself
  stores only what this phase's fields require, nothing extra.
- [x] Instant-vs-staff confirmation rule: request creation is never itself
  confirmation — for an `INSTANT` service the system performs `confirm()` as
  its own separate, immediately-following action within the same create
  call; for `STAFF_REVIEW` it stays `NEW` until staff acts. Proven by test.

## Persistence and concurrency — sub-packet 1A, done 2026-09-30

- [x] Forward-only Flyway migration `V5__travel_request_foundation.sql` in
  the Sharm To Go application.
- [x] Constraints mirroring every critical Kotlin invariant (status/actor/
  reason enums, sticky confirmedAt direction, contact-present, idempotency
  key uniqueness, reference format/uniqueness).
- [x] Repository ports and jOOQ adapters
  (`TravelRequestRepository`/`JooqTravelRequestRepository`).
- [x] Row-locking (`findByIdForUpdate` + `.forUpdate()`) for every mutating
  transition.
- [x] Idempotency: client-supplied key, unique DB constraint as the real
  safety net, a `findByIdempotencyKey` fast-path pre-check.
- [x] **Proven** concurrent duplicate requests do not create duplicate
  records: 8 parallel threads submitting the same idempotency key against
  real PostgreSQL via Testcontainers — exactly one row created, verified by
  `TravelRequestServiceTest."concurrent creates with the same idempotency
  key never produce two rows"`.
- [x] Capacity: this phase's capacity is the single request's own party size
  against the snapshotted option's `maxParticipants` — a plain validation,
  not a shared cross-request pool (this product has no per-day slot/calendar
  concept yet, unlike Safari's `TourSlot`; that is out of this phase's scope
  and explicitly noted as such in `TravelRequest.kt`'s class doc). Proven by
  `"party size over the snapshotted option's maxParticipants is rejected"`.
- [x] Audit events with correlation id, actor (typed CUSTOMER/STAFF/SYSTEM),
  from/to states and typed reason
  (`TravelRequestAuditRecorder`/`JooqTravelRequestAuditRecorder`,
  `travel_request_audit_event` table).

## API and security

- [ ] Public create-request endpoint with strict validation and rate-limit plan.
- [ ] Public reference/status lookup that does not expose another customer's PII.
- [ ] Staff list/detail endpoints with roster vs full-record projections.
- [ ] Separate staff permissions for view, review, confirm, cancel and complete.
- [ ] Update OpenAPI schemas, errors and examples.
- [ ] Return clean 400/404/409 responses; never raw database errors.

## Summary and WhatsApp

- [ ] Generate one stable customer-visible summary from stored snapshot facts.
- [ ] Include reference, service, date, party, pickup and confirmed/awaiting state.
- [ ] Create contextual WhatsApp text containing reference but no unnecessary PII.
- [ ] Ensure opening or sending WhatsApp does not mutate confirmation state.
- [ ] Add print/PDF only after the HTML summary is stable and accessible.

## Required evidence

- [x] Domain transition tests — `TravelRequestDomainTest.kt`, 20 tests.
- [x] Testcontainers migration/invariant tests — `TravelRequestServiceTest.kt`
  runs every case against real PostgreSQL via Testcontainers, including the
  `ProductIsolationIntegrationTest` update proving V5 migrates cleanly.
- [ ] HTTP validation and permission tests. (1B: no controller yet.)
- [x] Idempotency and capacity concurrency tests — see the 8-thread
  concurrent-duplicate proof above.
- [ ] Public projection privacy tests. (1B: no public endpoint yet.)
- [ ] OpenAPI validation. (1B: no contract added yet.)
- [x] Full Divers regression gate — `:platform:application:check` green,
  unaffected (sharm-to-go's migrations live in a disjoint application).
- [ ] Synthetic live walkthrough recorded in the evidence file. (1B/API-layer.)

## Exit gate

- [ ] One synthetic published service can receive a request and return a public
  reference without claiming confirmation.
- [ ] A staff-authorized confirmation produces an immutable commercial snapshot.
- [ ] Duplicate/concurrent submissions are safe and auditable.
