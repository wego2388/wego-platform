# Phase 2 — operations ERP

## Dependency gate

- [ ] Phase 1 API and lifecycle are complete and documented.
- [ ] Staff permission codes and roster/detail response shapes are stable.

## Navigation and dashboard

- [ ] Add Requests/Bookings to the existing Sharm To Go app shell.
- [ ] Replace placeholder dashboard counts with real permission-scoped queries.
- [ ] Show new, in-review, confirmed, today/upcoming and exception counts.
- [ ] Never call an endpoint the signed-in account lacks permission to use.

## Request queue

- [ ] Paginated queue with status/date/service/source filters.
- [ ] Search by public reference and permitted customer fields.
- [ ] Clear empty/loading/error/no-permission states.
- [ ] New/unclaimed work is visually distinct without relying on color alone.
- [ ] Deep links preserve filter state where practical.

## Request detail and actions

- [ ] Show customer contact, service/price snapshot, party, pickup, notes and
  source.
- [ ] Show audit timeline and current confirmation/payment independence.
- [ ] Review/claim action.
- [ ] Confirm action with current capacity and price revalidation.
- [ ] Cancel/expire/complete actions with typed reasons and confirmation dialogs.
- [ ] Copy/share customer-safe summary and WhatsApp message.
- [ ] Prevent double action through disabled/pending states and backend safety.

## Catalog support for conversion

- [ ] Show whether each published service is request-ready.
- [ ] Surface missing photo rights, schedule, capacity or policy as actionable
  readiness gaps.
- [ ] Add related-service and featured-service controls only when backed by a
  durable model, not a local UI-only flag.

## Required evidence

- [ ] Vitest coverage for permissions, queue filters and every transition UI.
- [ ] Typecheck, lint and production build.
- [ ] Real browser lifecycle against an isolated backend/database.
- [ ] Accessibility check for keyboard, dialogs, tables/lists and status labels.
- [ ] Existing catalog ERP tests remain green.

## Exit gate

- [ ] Staff can take a new request from receipt to confirmation/completion using
  only the ERP, with every transition visible in audit history.
