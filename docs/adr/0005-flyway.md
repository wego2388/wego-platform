# ADR-0005: Flyway migrations

- Status: Accepted
- Date: 2026-08-08

## Context

Schema change needs deterministic ordering, auditability, and the same path in development, tests, and production.

## Decision

Flyway is the only schema migration mechanism. Disable automatic schema creation by application frameworks. Never edit an applied release migration; forward-fix it.

## Consequences

Every schema change is reviewed SQL. Migration and rollback/forward-recovery considerations are part of delivery. Startup may validate/migrate only under an explicitly configured deployment policy.
