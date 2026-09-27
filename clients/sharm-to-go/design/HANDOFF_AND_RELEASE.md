# Handoff and release checklist

## Design ready

- [ ] Screen exists in `SCREEN_CATALOG.md` with priority and states.
- [ ] Uses semantic tokens and an existing component or records why a new one is needed.
- [ ] Desktop/mobile and ar/en variants are reviewed.
- [ ] Loading, empty, error, unavailable and permission behavior are specified.
- [ ] Copy identifies whether data is real, sample, fallback or pending approval.
- [ ] Money, policy, provider, pickup and confirmation meanings are explicit.
- [ ] Keyboard, focus order, live announcements and contrast are reviewed.

## Engineering ready

- [ ] API/state contract is versioned and authorization is named.
- [ ] The selected client artifact contains only release-lock product code,
      routes, permissions and migrations; its embedded lock digest is verified.
- [ ] Server—not the browser—owns price, capacity and payment truth.
- [ ] Public booking references grant no access; guest management and recovery
      use the reviewed capability/verification contract.
- [ ] PII minimization, access, retention and anonymization are approved before
      the first schema that stores customer data.
- [ ] Analytics contains no unnecessary contact or payment data.
- [ ] Locale fallback and stale-translation behavior are defined.
- [ ] Error messages have a safe customer action and an operator correlation path.
- [ ] No production secret or copied external asset enters source control.

## Release ready

- [ ] All automated gates and real-browser flows pass.
- [ ] Content, service ownership, media rights, price and policy are approved.
- [ ] Every enabled online payment method is live-approved and verified with
      provider test cases; a no-online-payment launch says so explicitly.
- [ ] Refund, reconciliation, callback failure and provider outage are rehearsed
      for every enabled payment method.
- [ ] Retention, support access, backup/restore and incident paths are active.
- [ ] The exact deployed artifact, embedded lock/digest and reviewed client lock
      match, and cross-product route/table/permission absence is proven.

## Decision log template

```text
Decision:
Owner:
Date:
Surfaces affected:
Approved source/evidence:
Alternatives considered:
Expiry/review date:
```
