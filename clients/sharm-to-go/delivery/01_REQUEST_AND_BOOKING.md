# Phase 1 — request and booking foundation

## Dependency gate

- [ ] Phase 0 session-safety checks are complete.
- [ ] Current public catalog contract and DB migrations have been inspected.
- [ ] Review tier is classified before schema/public-write work starts.

## Contract and domain

- [ ] Decide and document the canonical aggregate name (`TravelRequest` unless
  implementation review proves a better bounded-domain term).
- [ ] Define public reference format that contains no PII and is not guessable.
- [ ] Define states: `NEW`, `IN_REVIEW`, `CONFIRMED`, `COMPLETED`, `CANCELLED`,
  `EXPIRED`.
- [ ] Define guarded transition table, actor requirements and typed reasons.
- [ ] Snapshot service id/name, option, price/currency/basis and cancellation
  policy at request creation.
- [ ] Store requested date/time, adults, children, hotel/pickup, locale, notes
  and source channel.
- [ ] Require customer name and at least one valid reachable contact.
- [ ] Define consent, retention and redaction policy for customer PII.
- [ ] Define instant-vs-staff confirmation rule without treating request receipt
  as confirmation.

## Persistence and concurrency

- [ ] Add forward-only Flyway migration in the Sharm To Go application.
- [ ] Add constraints mirroring every critical Kotlin invariant.
- [ ] Implement repository ports and jOOQ adapters.
- [ ] Implement row-locking for every mutating read/modify/write operation.
- [ ] Implement idempotency key + canonical request fingerprint.
- [ ] Prove concurrent duplicate requests do not create duplicate records.
- [ ] Prove capacity cannot be oversold when confirmation reserves capacity.
- [ ] Add audit events with correlation id, actor, from/to states and reason.

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

- [ ] Domain transition tests.
- [ ] Testcontainers migration/invariant tests.
- [ ] HTTP validation and permission tests.
- [ ] Idempotency and capacity concurrency tests.
- [ ] Public projection privacy tests.
- [ ] OpenAPI validation.
- [ ] Full Divers regression gate.
- [ ] Synthetic live walkthrough recorded in the evidence file.

## Exit gate

- [ ] One synthetic published service can receive a request and return a public
  reference without claiming confirmation.
- [ ] A staff-authorized confirmation produces an immutable commercial snapshot.
- [ ] Duplicate/concurrent submissions are safe and auditable.
