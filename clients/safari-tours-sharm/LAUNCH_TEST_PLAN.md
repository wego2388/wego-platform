# Safari Tours Sharm — launch test plan (roadmap 5-1)

Every row says **how** it is proven. `AUTO` rows run in CI on every push
against the isolated compose stack with the mock Paymob client; `MANUAL` rows
need something the owner still has to provide (Paymob sandbox keys, domain,
e-mail provider, real phones) and are run once, together, before go-live.

Test ids: `E*` = `e2e/tests/safari-checkout.spec.ts`, `S*` =
`e2e/tests/safari-site.spec.ts`, `L*` = `e2e/tests/safari-launch.spec.ts`,
`B:` = backend test class (`platform/application/src/test/.../toursoperator`).

## 1. Languages × pages

| Page | EN | AR (RTL) | RU | IT |
|---|---|---|---|---|
| Home, tours list (server-rendered) | AUTO S1 | MANUAL look | MANUAL look | MANUAL look |
| Tour page + calendar → checkout | AUTO S3 | MANUAL look | MANUAL look | MANUAL look |
| Checkout form | AUTO E10 | AUTO L1 (phone) | AUTO L2 | AUTO L2 |
| Payment result — failed | — | AUTO L1 | — | — |
| Confirmation | AUTO E10 | MANUAL | MANUAL | MANUAL |
| My booking lookup | AUTO L4 | MANUAL | MANUAL | MANUAL |
| Info pages, 404 | AUTO S4 (all four) | AUTO S4 | AUTO S4 | AUTO S4 |
| Accessibility (axe WCAG 2.1 AA) | AUTO S7 | AUTO S7 | — | — |

"MANUAL look" = a person who reads the language checks wording and layout
once on a phone; the content itself was reviewed per language (WEGO-016-CNT).

## 2. Devices

| Device | How |
|---|---|
| Desktop Chrome | AUTO (all suites) |
| Phone size (iPhone 13 viewport, Arabic) — no sideways scroll | AUTO L1 |
| Real iPhone Safari + real Android Chrome, full booking with sandbox card | MANUAL (needs sandbox keys + domain) |
| Slow 4G | measured in UX-8 (LCP 0.7–0.8 s); re-measure on the real server |

## 3. Payment outcomes

| Outcome | Expected | How |
|---|---|---|
| Success | booking CONFIRMED, payment PAID, confirmation page, e-mail queued | AUTO E3/E6/E7/E10, B: notification tests |
| Declined card | payment FAILED, booking not confirmed, "payment not completed" page | AUTO L1 |
| Duplicate webhook | processed once (`already_processed`) | AUTO E4 |
| Forged webhook (bad HMAC) | 400, nothing changes | AUTO E5 |
| Customer abandons checkout | booking expires after 30 min, places released | `BookingTest` (domain) + `ToursOperatorPaymentTest` (overdue expiry) |
| Webhook arrives late / after expiry | recorded, flagged for staff review, never silently lost | B: D6f, D6g |
| Refund | recorded against the payment | B: D6e, D10a; MANUAL in sandbox |
| Pay twice from two tabs | one payment only (idempotent resume) | AUTO E2b |
| Real Paymob sandbox: success, decline, timeout, 3-D Secure | as above | MANUAL — **needs Paymob sandbox keys** |

## 4. Booking states and capacity

| Case | How |
|---|---|
| NEW → CONFIRMED → COMPLETED | AUTO E7, B: lifecycle tests |
| Staff cancellation visible to the customer | AUTO L4 |
| Party larger than the places left | AUTO L5 (API), B: capacity tests |
| Two customers race for the last places | B: concurrent booking test |
| Per-unit tours (buggy, boat, transfer) take all their seats | B: per-unit pricing tests |
| Blocked or inactive departure | B: slot/tour tests |

## 5. Emergency sales switch

| Case | How |
|---|---|
| Pause → site warns, API refuses new bookings → resume | AUTO L3 |
| Payment already in progress still confirms while paused | B: sales-control tests |
| Only managers can switch | B: sales-control tests |

## 6. Staff (ERP)

| Case | How |
|---|---|
| Login, bookings list shows the confirmed booking | AUTO E9 |
| Today run sheet, finance, notifications | unit tests + MANUAL walk-through with the owner |
| Logout revokes the session | AUTO E9 |

## 7. Operations (before go-live, on the real server)

| Case | How |
|---|---|
| Backup + restore drill, timed | MANUAL — `scripts/safari-ops/restore-drill.sh`; record time |
| Health check green, alert reaches the owner | MANUAL — **needs the alert channel** |
| Confirmation e-mail arrives (SPF/DKIM pass, not spam) | MANUAL — **needs SMTP provider + domain** |
| HTTPS, certificate auto-renewal | MANUAL — **needs domain** |
| Stop-then-start upgrade rehearsal on a copy | MANUAL — runbook section 5 |

## Go-live gate

All `AUTO` rows green on the release commit, every `MANUAL` row ticked with
date and who checked it, and the owner's written OK (roadmap 5-2).

---

## بالعربي لمحمد

دي قايمة بكل الحالات اللي لازم تتجرب قبل ما الموقع يشتغل للناس:
- اللغات الأربعة
- الموبايل والكمبيوتر
- الدفع: نجح، اترفض، اتكرر، اتأخر
- الحجز: اتأكد، اتلغى، العدد زاد عن الأماكن
- زرار الطوارئ

اللي مكتوب جنبه **AUTO** بيتجرب أوتوماتيك مع كل تعديل. اللي جنبه **MANUAL**
محتاج حاجة منك الأول: مفاتيح Paymob التجريبية، الدومين، مزوّد الإيميل،
وطريقة التنبيه. ونجربهم كلهم مع بعض مرة واحدة قبل الإطلاق.
