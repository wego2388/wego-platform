# Wego Architecture

## System identity

Wego is a platform for multiple vertical products, not a generic client-specific ERP. A deployed system is composed from four layers:

```text
Wego Platform
  + Wego Product
  + Client Configuration
  = isolated Client Deployment
```

The first composition is Platform + Wego Divers + Sharm Divers Club. Sharm proves the composition mechanism; it does not define shared architecture. El Kheima Beach Resort OS is a separate production system and is outside every Wego runtime and build boundary.

## Runtime shape

The initial client instance is a modular monolith behind Nginx with PostgreSQL as durable truth and Redis for explicitly ephemeral responsibilities. It is packaged for a single-client VPS with Docker Compose. No Kafka, Kubernetes, service mesh, or microservice split is part of the foundation.

```text
Web / Mobile
    │ OpenAPI v1
Nginx edge
    │
Spring Boot modular monolith ─── Redis (ephemeral only)
    │ transaction
PostgreSQL ─── transactional outbox ─── post-commit workers/integrations
```

## Source architecture

- `platform/`: proven cross-product kernel and capabilities
- `products/`: vertical industry behavior
- `clients/`: client-owned manifests, branding, and configuration
- `web/`: Nuxt applications and shared web packages
- `mobile/`: KMP shared code and distinct Ops/Customer experiences
- `foundry/`: manifest schemas, validation, and deterministic release metadata
- `control-plane/`: platform-operations contracts only; no client business data
- `infrastructure/`: local/deployment topology and edge configuration
- `docs/`: architecture, decisions, security, operations, and execution evidence

Only directories with executable or decision-bearing content are created.

## Backend module model

Spring Modulith verifies logical modules and prevents cycles/internal access. The application composition root may depend on module APIs and infrastructure wiring. A business module may contain:

```text
domain/          pure rules, types, events
application/     use cases, ports, transaction orchestration
infrastructure/  jOOQ, Redis, external adapters
api/             HTTP/contract mapping
```

Layers appear only when needed. Domain dependencies point inward. Cross-module interaction uses exposed APIs or domain/integration events, never another module's infrastructure.

## Transaction and event model

A command validates authorization and business invariants inside one PostgreSQL transaction. Durable integration events are inserted into the outbox in that transaction. A worker claims committed rows using bounded locking, delivers through an adapter, and records attempts/outcomes. Delivery is at least once, so consumers and handlers must be idempotent.

## Client isolation

Each client deployment has one database, Redis configuration/instance, secret set, object-storage namespace, and backup chain. Schema tables do not carry universal `tenant_id` columns. Client identity is deployment configuration, not a row-level tenant selector.

## Control plane boundary

The future Wego Control Plane stores deployment identity, desired/actual release, health, backup metadata, and audited support grants. It does not mirror operational business records. A Wego operator identity alone grants no access to client data. Support access must be initiated through a time-bound grant recognized and audited by the client instance.

## Foundation scope

WEGO-000 establishes executable boundaries, health/security defaults, explicit persistence, contracts, manifests, build/test infrastructure, and narrow web/mobile shells. It does not implement operational product capabilities or the control plane.
