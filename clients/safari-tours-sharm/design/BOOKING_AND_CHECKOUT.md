# Booking and checkout — Safari Tours Sharm

The booking flow is a 4-step wizard. No customer account is required.
A booking is not confirmed before payment succeeds — never represent a
`NEW` or `PENDING` booking as confirmed to the customer.

---

## Step flow

```
/book/:tourSlug
  step=1  →  Customer details
  step=2  →  Review booking
  step=3  →  Payment (Paymob hosted page — external redirect)
  step=4  →  Confirmation  (/booking/:id/confirmation)
```

Progress indicator shows steps 1–4 with labels in the active locale.
Step transitions: current step slides left on forward, slides right on back.
The progress bar fills smoothly with a 300ms CSS transition.

---

## Step 1 — Customer details

Fields:
- Full Name * — text input, min 2 chars
- Nationality * — searchable dropdown with country flags, ISO 3166-1 alpha-2
- Phone Number * — international tel input with country code dial picker
- Hotel Name * — text input (pickup logistics)
- Room Number — optional text input
- Special Requests — textarea, max 500 chars, optional

Validation fires on blur and on [Continue] click.
Error messages are linked to inputs via `aria-describedby`.
Required marker is not color-only — marked with `*` and `aria-required="true"`.

---

## Step 2 — Review booking

Immutable summary (read-only):
- Tour name + cover thumbnail
- Date (formatted in active locale, e.g. "Friday, 3 October 2026")
- Time slot (e.g. "Sunset — approx. 5:00 PM")
- Adults: count × price = subtotal
- Children: count × price = subtotal (row hidden if count is 0)
- Separator
- Total in EUR (bold, large)
- Free cancellation deadline ("Free cancellation until 2 Oct 2026")

Required before [Confirm & Pay]:
- Checkbox: "I agree to the terms and cancellation policy" — `aria-required`
- Error state if unchecked on submit

Prices shown here are a snapshot. The backend captures `totalEUR` at booking
creation time (immutable). If prices change after this screen, the snapshot
is what was agreed.

---

## Step 3 — Payment

Redirect to Paymob hosted payment page.
Safari Tours Sharm has a separate Paymob account — credentials are never
shared with sharm-divers-club or sharm-to-go.

Supported methods (configured in Paymob):
- Credit/Debit Card (Visa, Mastercard)
- Vodafone Cash
- Fawry
- Apple Pay / Google Pay

On Paymob success → redirect to `/booking/:id/confirmation`
On Paymob failure → return to Step 3 with error message + retry option.
Payment timeout: 30 minutes from booking creation (booking expires → EXPIRED).

Never collect card numbers inside Wego forms. The Paymob hosted page owns
all sensitive card entry — Wego never sees card data.

---

## Step 4 — Confirmation

Triggered only after payment status transitions to `PAID`.

Content:
- Lottie checkmark animation (800ms, plays once)
- "Booking Confirmed!" headline
- Booking reference: `STR-2026-NNNN` (prominent, copyable)
- Tour: name, date, time
- Guests: adults + children
- Total paid: EUR amount
- Pickup note: "We will contact you on WhatsApp before your trip"
- WhatsApp contact link
- [Add to Calendar] — generates .ics download
- [Book Another Tour] — links to /tours

After confirmation, two WhatsApp notifications are sent automatically:
1. To the customer: booking confirmation + pickup details
2. To Safari Tours operator: new booking alert

---

## My booking (/booking/:id)

No account required. Lookup by:
- Booking reference (e.g. STR-2026-4821)
- Phone number used at booking time

States:
- Found + active: full booking details + cancel button if within free window
- Found + cancelled: cancellation details
- Found + completed: tour complete, review request CTA
- Not found: friendly error, WhatsApp CTA

Cancel flow:
- Button visible only within free cancellation window (default 24h before trip)
- Confirm dialog: "Are you sure? This action cannot be undone."
- On confirm: booking → CANCELLED, refund initiated if PAID
- Customer receives WhatsApp cancellation confirmation
