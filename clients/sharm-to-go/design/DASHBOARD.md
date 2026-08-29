# Operations dashboard design

## Launch workspace

The first dashboard is for Sharm To Go staff. A provider portal is a later,
separately scoped surface; adding `provider` to a broad role is not acceptable.

## Primary work queues

1. Bookings needing confirmation.
2. Payments needing verification or reconciliation.
3. Today's pickups and operational exceptions.
4. Services with missing price, capacity, policy or translation.
5. Refund requests and failed provider operations.

Cards link to filtered records. Counts are never hard-coded design decoration.

## Key editors

- Service: identity, category, fulfilment label, duration, inclusions,
  restrictions, pickup, policy, media rights, locales and publication.
- Calendar: slot, capacity, price basis, participant bands and closures.
- Booking: immutable selection/price snapshot, customer contact, timeline,
  internal notes with author/time, payment and customer-visible status.
- Payment: attempts, provider references, verified callback timeline, refunds and
  reconciliation—without card details.

## Permission-oriented actions

Viewing bookings, editing catalog, changing capacity/price, confirming a booking,
starting a refund, viewing restricted customer data and managing users are
separate permissions. Disabled actions identify the missing permission without
exposing restricted record content.

## Arabic/English behavior

Tables retain column meaning in RTL rather than mechanically reversing money and
reference values. Dates use an explicit timezone. Booking/provider references,
email, phone and currency use isolated LTR formatting. Long Arabic labels wrap;
they are not truncated to preserve an English-sized component.
