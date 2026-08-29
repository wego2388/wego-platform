# Payment foundation

## Recommended launch composition

- CIB business account: settlement account, subject to each merchant contract.
- Paymob: primary hosted checkout for enabled cards and mobile wallets.
- FawryPay: reference-code alternative for customers who prefer cash/agent
  payment; enable other Fawry methods only when they add a real customer need.
- Cash on arrival: service-level option, never a global promise.
- Direct CIB e-commerce gateway: evaluate after commercial terms; do not operate
  a duplicate card route at launch without a measured resilience/cost reason.

This is a product/technical recommendation, not a claim that merchant accounts
or methods are approved. Credentials, fees, settlement timing, supported cards,
currencies, refunds and chargebacks are confirmed from signed provider terms.

## Security boundary

- Hosted checkout or provider-owned secure fields collect card data.
- Wego stores no PAN, CVV, wallet PIN or raw provider secret.
- Success in a browser redirect is not proof of payment.
- Only an authenticated, signature-verified server callback plus provider query
  can advance a payment attempt.
- Callback receipt is idempotent and preserves the provider event identity.
- Secrets live in the deployment secret manager and rotate without code edits.

## Durable payment model for a later packet

```text
Booking
└── Payment intent (amount/currency/purpose)
    ├── Attempt 1 — PAYMOB — FAILED
    ├── Attempt 2 — FAWRY — EXPIRED
    └── Attempt 3 — PAYMOB — PAID
        └── Refund — PARTIAL / SUCCEEDED
```

Minimum attempt states: `CREATED`, `PENDING`, `PAID`, `FAILED`, `EXPIRED`,
`CANCELLED`. Refunds are separate records with `REQUESTED`, `PROCESSING`,
`SUCCEEDED`, `FAILED`; partial refunds never rewrite the original captured
amount.

## Operations requirements

- Search by booking reference and provider transaction reference.
- Show gross amount, refunded amount, outstanding amount and settlement status.
- Manual state edits are forbidden; operators may retry verification or start an
  authorized refund with a reason.
- Daily reconciliation compares Wego attempts, provider reports and CIB credits.
- Alerts cover paid-without-booking, booking-without-paid-attempt, duplicate
  provider reference, callback failures and settlement mismatches.

## Activation inputs

Signed merchant contracts, legal merchant name, CIB settlement details, Paymob
and Fawry sandbox/live credentials, webhook signing method, enabled methods,
currency/fee/settlement/refund rules, support contacts and test cases. No secret
belongs in this directory.
