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
- [ ] Server—not the browser—owns price, capacity and payment truth.
- [ ] Analytics contains no unnecessary contact or payment data.
- [ ] Locale fallback and stale-translation behavior are defined.
- [ ] Error messages have a safe customer action and an operator correlation path.
- [ ] No production secret or copied external asset enters source control.

## Release ready

- [ ] All automated gates and real-browser flows pass.
- [ ] Content, service ownership, media rights, price and policy are approved.
- [ ] Payment method is live-approved and verified with provider test cases.
- [ ] Refund, reconciliation, callback failure and provider outage are rehearsed.
- [ ] Retention, support access, backup/restore and incident paths are active.
- [ ] The exact deployed client lock matches the reviewed lock.

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
