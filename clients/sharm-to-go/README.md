# Sharm To Go

Sharm To Go now exists in the correct place: this is the isolated client profile
for `wego-travel-marketplace` inside the shared `/home/wego/wego-platform`
repository. It sits alongside `clients/sharm-divers-club`; neither client is
nested inside or coupled to the other.

## What is executable now

- Start with [`CLAUDE_HANDOFF.md`](CLAUDE_HANDOFF.md) before continuing work.
  It records the current branch/worktree boundary, completed packets, next
  authorized scope, required toolchains, and the exact quality gate.

- `client.manifest.json` declares the client, product, Cairo timezone, initial
  Arabic/English locales, EGP organizational currency, and isolated deployment.
- `release.lock.json` deterministically resolves only the Travel Marketplace
  product and its platform dependencies as Foundry metadata. As of Packet 0R
  (2026-09-02, self-verified, independent Tier 1 review outstanding — see
  `TECHNICAL_EXECUTION_PLAN.md` and the WEGO-010-A board entry), this is also
  a proven runtime claim: `platform/apps/sharm-to-go` is a separate, real
  Spring Boot application from `platform/application` (Sharm Divers Club),
  compiled with `products/travel-marketplace` on its classpath and
  `products/divers` deliberately absent, with its own Flyway migration
  location containing only the shared platform/identity foundation. A live
  run against a real throwaway PostgreSQL confirmed zero `divers_*` tables,
  zero Divers permissions, and zero `com.wego.divers.*` classes in this
  app's jar.
- `products/travel-marketplace` now contains the real Packet 1A catalog domain
  and API for providers, categories, services, publication workflow, and the
  narrow public projection. No launch content is seeded.
- `web/apps/sharm-to-go-site` now has the Packet 1C Arabic/English public
  catalog list and service-detail routes backed by that public projection. It
  retains an honest empty state while no real service is published, and its
  `/booking-preview` route remains explicitly non-transactional.
- `web/apps/sharm-to-go-erp` now has the Packet 1B login and real provider,
  category, and service-management screens. It is not deployed and contains
  no real client data.
- `mobile/apps/sharm-to-go` (+ `-android`) now has the Packet 1D Home/
  Experiences list/detail screens, backed by `mobile/shared`'s
  `TravelCatalogSnapshot` (bundled, versioned per release). A real debug APK
  builds. Packet 1E, a real owner-approved launch service, has not started.

## Decision documents

- [Service content research](content-research/README.md) — text-only source
  inventories and 37 original Sharm To Go draft concepts spanning the earlier
  private-tour subset and the broader Sharm gap audit; research only, never
  approved inventory or publishable prices.
- [Product blueprint](PRODUCT_BLUEPRINT.md)
- [Service ownership model](SERVICE_OWNERSHIP.md)
- [Locale and content matrix](LOCALES_AND_CONTENT.md)
- [Reference study](REFERENCE_STUDY.md)
- [Execution plan](EXECUTION_PLAN.md) — phase overview; see
  [Technical execution plan](TECHNICAL_EXECUTION_PLAN.md) for the real
  schema/screens/tests detail behind each phase, across website, dashboard,
  and the new dedicated mobile app.
- [Marketplace expansion plan](MARKETPLACE_EXPANSION_PLAN.md) — the
  owner-approved sequencing beyond Activities: Dining, Accommodation, Car
  rental, and why WhatsApp/social automation stays deliberately deferred.
- [Complete design handoff](design/README.md)

## Executable design routes

- `/experiences` — real public catalog list; empty until approved content is
  published through the catalog workflow.
- `/experiences/:id` — real public service detail for a published service.
- `/booking-preview` — Arabic/English responsive booking and payment pattern;
  explicitly creates no booking or payment.
- `/design-system` — living semantic-token and component-state inventory.

No external reference image, code, description, price, review, or provider fact
is a build/runtime dependency. The original screenshots and local research paths
remain discovery material only.
