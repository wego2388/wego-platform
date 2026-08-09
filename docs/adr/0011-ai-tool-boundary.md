# ADR-0011: AI typed-tool boundary

- Status: Accepted
- Date: 2026-08-08

## Context

An owner copilot may assist operations, but model output is probabilistic and external providers are untrusted processors.

## Decision

AI orchestration remains in the Kotlin platform initially. Models may request only typed tools that independently enforce authentication, authorization, scope, domain validation, confirmation/approval, transactions, and audit. Models receive no database credentials or unrestricted SQL.

## Consequences

Provider changes do not bypass business controls. Sensitive actions require human confirmation. A Python service requires a demonstrated ML/CV/scientific workload and a separate ADR.
