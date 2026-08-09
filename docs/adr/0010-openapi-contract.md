# ADR-0010: Versioned OpenAPI contract

- Status: Accepted
- Date: 2026-08-08

## Context

Web, mobile, and external consumers need one reviewable client/server contract.

## Decision

OpenAPI is the versioned HTTP contract. Validate it in CI and automate client generation where practical. Domain types and transport schemas remain separate.

## Consequences

Compatibility is reviewed before implementation drift. Breaking changes require a new API version or managed transition. Generated artifacts are reproducible, not manually edited.
