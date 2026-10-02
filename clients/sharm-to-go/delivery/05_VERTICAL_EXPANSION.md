# Phase 5 — vertical expansion

Do not start a vertical merely to fill the homepage. Each requires real owner
content and a domain shape matching its operational truth.

## Shared prerequisite

- [ ] Activities request loop works end to end on web, ERP and mobile.
- [ ] Category queries are scoped by vertical so records cannot leak between
  Activities, Dining, Cars and Stays.
- [ ] Shared media rights, localization and publication rules are proven.

## Dining — restaurants and cafés

- [ ] Approve `Venue` aggregate and vertical-scoped category migration.
- [ ] Define stable area codes/labels and venue type.
- [ ] Model operator, address and ordered rights-cleared media.
- [ ] Add cuisine/area/type filters.
- [ ] Add information/table-request flow without pretending walk-in inventory.
- [ ] Add ERP venue/content/request screens.
- [ ] Add web and mobile directory/detail/request screens.
- [ ] Publish only owner-approved real venues.

## Car rental

- [ ] Define vehicle, class, seats, transmission, luggage and eligibility rules.
- [ ] Define dated availability, delivery/return area and driver requirements.
- [ ] Snapshot rate, deposit and included mileage/insurance terms.
- [ ] Add ERP inventory/request operation.
- [ ] Add web/mobile browse, detail and dated request.
- [ ] Publish only real vehicles/agencies and rights-cleared media.

## Accommodation

- [ ] Define property, room/unit, occupancy, board and stay-date invariants.
- [ ] Define availability authority and rate-expiry model.
- [ ] Support check-in/out, rooms, adults/children and special requests.
- [ ] Model cancellation separately from activity cancellation when required.
- [ ] Add ERP property/rate/request operation.
- [ ] Add web/mobile search, filters, detail and stay request.
- [ ] Do not display star ratings/reviews without authoritative sources.

## Offers

- [ ] Attach an offer to a real service/vehicle/venue/stay product.
- [ ] Require validity window, terms, eligibility and price evidence.
- [ ] Prevent expired/suspended inventory from remaining promoted.
- [ ] Track campaign source and conversion without customer PII.

## Exit gate

- [ ] Each launched vertical completes discovery -> detail -> request -> ERP
  handling through the shared customer relationship while preserving its own
  real invariants.
