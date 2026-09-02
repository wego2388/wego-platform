# Sharm To Go

Sharm To Go now exists in the correct place: this is the isolated client profile
for `wego-travel-marketplace` inside the shared `/home/wego/wego-platform`
repository. It sits alongside `clients/sharm-divers-club`; neither client is
nested inside or coupled to the other.

## What is executable now

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
- `web/apps/sharm-to-go-site` is an original Arabic/English public foundation.
  It shows discovery categories and provider responsibility, not fake services.
- `web/apps/sharm-to-go-erp` is an Arabic/English readiness dashboard. It is not
  deployed, authenticated, or connected to business APIs yet, and says so in
  the UI.

## Decision documents

- [Product blueprint](PRODUCT_BLUEPRINT.md)
- [Service ownership model](SERVICE_OWNERSHIP.md)
- [Locale and content matrix](LOCALES_AND_CONTENT.md)
- [Reference study](REFERENCE_STUDY.md)
- [Execution plan](EXECUTION_PLAN.md) — phase overview; see
  [Technical execution plan](TECHNICAL_EXECUTION_PLAN.md) for the real
  schema/screens/tests detail behind each phase, across website, dashboard,
  and the new dedicated mobile app.
- [Complete design handoff](design/README.md)

## Executable design routes

- `/booking-preview` — Arabic/English responsive booking and payment pattern;
  explicitly creates no booking or payment.
- `/design-system` — living semantic-token and component-state inventory.

No external reference image, code, description, price, review, or provider fact
is a build/runtime dependency. The original screenshots and local research paths
remain discovery material only.
