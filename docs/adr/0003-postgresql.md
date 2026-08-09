# ADR-0003: PostgreSQL durable store

- Status: Accepted
- Date: 2026-08-08

## Context

Business operations require strong constraints, transactions, explicit locking, rich indexing, and reliable backups.

## Decision

Use PostgreSQL as the durable source of truth for each isolated client deployment. Test persistence against real PostgreSQL, not an emulation database.

## Consequences

Schema and concurrency invariants can be enforced centrally. Operations must provide per-client backup/restore and upgrades. Redis remains non-authoritative.
