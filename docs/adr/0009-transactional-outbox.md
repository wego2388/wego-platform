# ADR-0009: PostgreSQL transactional outbox

- Status: Accepted
- Date: 2026-08-08

## Context

Notifications and external integrations must not make committed business transactions depend on unreliable networks.

## Decision

Persist integration events in a PostgreSQL outbox in the originating transaction. Workers claim, deliver, retry, and observe events after commit. Delivery is at least once.

## Consequences

Business commits survive provider failure. Handlers need idempotency, bounded retries, dead-letter/operator visibility, and retention. Kafka is not justified at bootstrap.
