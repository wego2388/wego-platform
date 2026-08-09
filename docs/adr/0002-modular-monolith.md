# ADR-0002: Modular monolith

- Status: Accepted
- Date: 2026-08-08

## Context

The platform needs explicit domain boundaries without distributed-systems overhead before scale and team topology justify it.

## Decision

Deploy one Spring Boot modular monolith. Use Spring Modulith and architecture tests to verify logical modules, cycles, and internal access.

## Consequences

Transactions and operations remain simple while modules can evolve independently. A future extraction requires measured coupling/scale evidence and a new ADR; repository folders alone are not services.
