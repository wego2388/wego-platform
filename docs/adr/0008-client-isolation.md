# ADR-0008: Isolated client deployments

- Status: Accepted
- Date: 2026-08-08

## Context

Initial production economics and security favor strong client isolation over shared-database SaaS complexity.

## Decision

Give each client one deployment, PostgreSQL database, Redis configuration/instance, secret set, storage namespace, and backup chain. Do not add ubiquitous `tenant_id` columns.

## Consequences

Failure, credentials, and backups are isolated. Release automation must handle multiple instances. A future shared-tenancy model would be a new architecture with migration and an ADR.
