# Wego Engineering Constitution

Status: normative for this repository.

## 1. Product integrity

Wego Platform is not any single product or client. Code placement follows demonstrated invariants:

- cross-product capability: `platform/`
- vertical industry behavior: `products/<product>/`
- one-client configuration or branding: `clients/<client-id>/`

Client customization uses configuration or explicit extension points. It never forks a product, alters shared rules for one customer, or introduces speculative shared abstractions.

## 2. Delivery control

Exactly one execution packet is active per implementation worktree. Work starts from an acceptance criterion and ends with executable evidence. A green test suite does not authorize the next mission. Commits, pushes, deployments, DNS changes, and production access require explicit authorization.

Review intensity is proportionate to risk: authentication, authorization, payments, database migrations, multi-tenant/client-isolation boundaries, and real client PII require independent adversarial review with executable evidence before commit; other work requires one verified pass. See `docs/operations/REVIEW_INTENSITY.md`.

## 3. Architecture

The deployable backend is a modular monolith. Modules expose intentional APIs and hide implementation packages. Business modules use `domain`, `application`, `infrastructure`, and `api` where those responsibilities exist; empty layers are not created.

Domain code expresses business rules with domain-specific types. It must not depend on HTTP, Spring MVC, jOOQ generated records, Redis, AI/provider SDKs, or infrastructure implementations. Generic base services and repositories require proven repetition and an explicit invariant.

## 4. Data and transactions

PostgreSQL is the durable source of truth. Flyway is the only schema migration path. jOOQ is the primary persistence approach. Monetary values use `BigDecimal`/`NUMERIC`; durable instants use UTC-aware types; database constraints enforce safe invariants.

Retryable commands are idempotent. Cross-boundary side effects occur after commit through a PostgreSQL transactional outbox. External delivery failure must not invalidate a committed business transaction.

## 5. Security and privacy

Authorization is server-side, deny by default, least privilege, and scoped. UI visibility is not authorization. No global client master credential exists. Support access is explicit, time-bounded, reasoned, approved where required, and audited.

Secrets never enter source control or logs. Data collection is minimized and PII is classified. Encryption is applied to justified fields with a key lifecycle, not as decorative complexity.

## 6. Contracts and clients

OpenAPI is the versioned client/server contract. Breaking changes require a version transition. Generated clients are reproducible outputs. Client deployments are isolated; a hypothetical shared SaaS future does not justify `tenant_id` on every table.

Mobile offline behavior is transactional architecture, not a banner. Local commands carry stable idempotency keys through an outbox and sync protocol. Conflict behavior is domain-specific.

## 7. Intelligence

AI models receive typed, minimized context and may request only registered tools. Tools independently enforce identity, authorization, permission, scope, domain validation, confirmation, transaction, and audit. Models never receive database credentials, unrestricted SQL, or authorization authority.

## 8. Quality and operations

Changes are complete only with relevant compilation, static analysis, focused invariant/failure tests, module verification, real PostgreSQL integration where meaningful, contract validation, and production builds. Coverage percentages do not replace risk-based tests.

Logs are structured, sensitive values are excluded, and correlation is designed across commands and outbox deliveries. Health, readiness, metrics, audit, backup, and restore responsibilities are explicit before production release.

## 9. Evolution

Technology-baseline changes require an ADR with compelling evidence. Database migrations are forward-fix by default and never edited after release. Foundry remains a constrained manifest/validation/release mechanism until real client variation proves further automation.
