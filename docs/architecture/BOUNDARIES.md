# Boundary Rules

## Placement test

Before adding a capability, answer in order:

1. Is the invariant proven useful across existing Wego products? Place it in `platform/`.
2. Is it an industry rule? Place it in `products/<vertical>/`.
3. Is it presentation/configuration unique to one client? Place it in `clients/<client-id>/`.
4. Is reuse only hypothetical? Keep it local to the first real owner.

Moving code toward the platform requires understood invariants and a deliberate refactor. Product and client directories never patch platform internals.

## Dependency policy

Allowed direction:

```text
client manifest → product manifest → platform capability metadata
application composition → module public APIs
api/infrastructure → application → domain
```

Forbidden direction:

- domain → Spring MVC, HTTP, jOOQ records, Redis, provider SDKs
- platform → client code or client manifest values
- one product → another product's internals
- control plane → client database
- UI visibility → authorization decision
- AI provider → database or unrestricted domain execution

## Public module API

Module-root types are public contracts by exception. Implementation packages are internal. Cross-module types must be small, stable, domain-oriented, and free of transport/persistence records. Spring Modulith verification and architecture tests enforce these rules.

## Configuration boundary

Configuration may select declared products/capabilities, supply validated values, and apply design tokens/content. It may not contain executable scripts, SQL, arbitrary class names, or authorization bypasses. Unknown manifest properties are rejected.

## Contract boundary

OpenAPI describes external HTTP behavior. Domain types do not double as wire types. API versioning, authentication requirements, idempotency headers, errors, instants, currencies, and locale semantics are explicit.

## Legacy boundary

Legacy systems may inform domain discovery through non-secret operational observation. They are not build dependencies, shared databases, source donors, authentication providers, migration targets, or integration assumptions during WEGO-000.
