# Information architecture

## Public navigation

```text
Home
├── Experiences
│   ├── Category
│   ├── Search and filters
│   └── Service detail
├── Plan your trip (later)
├── Saved (later)
├── Manage booking
├── Help / FAQ
├── Cart
└── Locale and currency display
```

Search, category and service-detail pages lead to the same booking component;
there is no second booking implementation per category.

## Checkout route family

```text
/experiences/:slug
/checkout/options
/checkout/details
/checkout/payment
/checkout/result
/booking/:publicReference
```

The executable foundation uses `/booking-preview` only. Production routes above
stay reserved until real catalog and booking contracts are implemented.

## Operations navigation

```text
Overview
Catalog
├── Categories
├── Services
├── Options and add-ons
└── Content and translations
Calendar and pricing
Bookings
Customers
Payments and refunds
Coupons
Providers (later scoped workspace)
Reports
Settings
├── Pickup zones
├── Policies
├── Payment methods
├── Locales
└── Users and permissions
```

Navigation visibility follows permissions. Hiding a link never replaces API
authorization.

## Core object names

- **Category:** discovery grouping, not a bookable item.
- **Service:** the public bookable product description.
- **Option:** a concrete duration/package/pricing configuration.
- **Slot:** a dated start time and capacity fact.
- **Add-on:** optional priced item tied to an option.
- **Booking:** the customer commitment and immutable commercial snapshot.
- **Payment attempt:** one provider transaction attempt; never the booking itself.
