# Sharm To Go — technical execution plan and Claude handoff

This is the engineering-detail companion to `EXECUTION_PLAN.md`. It translates
the product, design, ownership, locale, booking, payment, accessibility, SEO,
and operations documents in this directory into small executable packets.

**Reviewed 2026-08-30 against the repository's actual code and build.** This is
planning only. WEGO-011 remains the sole `ACTIVE` packet and WEGO-010-A remains
`PAUSED`. Nothing in this document authorizes a board status change, a schema
change, implementation, commit, push, deployment, or production access.

## Claude: read this before doing any work

When the owner explicitly authorizes Sharm To Go implementation:

1. Read `docs/ENGINEERING_CONSTITUTION.md`,
   `docs/execution/WEGO_EXECUTION_BOARD.md`, this document, and every Sharm To
   Go document named by the selected packet.
2. Confirm that no other packet is `ACTIVE`. Do not silently pause, close, or
   replace WEGO-011. Update the board only when the owner has authorized the
   status change.
3. Work on exactly one packet from the map below. Do not combine backend,
   dashboard, website, mobile, payment, or launch work just because adjacent
   packets are documented here.
4. Record the packet's exact scope, acceptance tests, risk tier, and evidence
   commands on the board before implementation. Tier 1 packets require an
   independent review with zero unresolved blocking findings before closure.
5. Re-read the relevant existing implementation before copying a pattern.
   Reuse an invariant only when it is genuinely product-neutral; never copy
   Sharm Divers Club business data, copy, permissions, routes, or semantics.
6. Use synthetic fixtures in tests. Do not invent or publish a real service,
   provider, price, policy, translation, photo, availability, or credential.
7. Stop at every owner-decision gate listed below. Unknown business facts stay
   unknown and block activation; they are never replaced by a plausible value.
8. Finish with the changed-file list, commands and results, migration evidence,
   residual risks, and owner inputs still required. Leave changes uncommitted
   unless the owner separately and explicitly asks for a commit. Never push or
   deploy without separate explicit authorization.

## Non-negotiable product rule

**No invented service, price, provider, photo, availability, or policy — ever.**
Engineering may create schemas, forms, empty states, and clearly synthetic test
fixtures. A real record may reach `PUBLISHED` only from an owner-approved
`design/SERVICE_CONTENT_TEMPLATE.md` submission with traceable source and media
rights evidence.

## Review finding that changes the critical path

Phase 0 produced valid manifests and deterministic release locks, but the lock
is not yet an executable deployment boundary:

- `platform/application/build.gradle.kts` adds both
  `products/divers/src/main/kotlin` and
  `products/travel-marketplace/src/main/kotlin` to the same application.
- `platform/application/src/main/resources/application.yml` points Flyway at
  one global `classpath:db/migration` location. Existing V3–V8 migrations are
  Divers migrations and currently run for that application regardless of a
  client lock.
- the application security configuration currently describes the Divers API;
  no runtime code reads a client release lock to select controllers, beans,
  permissions, or migrations.

Therefore the current statement that the two clients "compose independently"
is true for Foundry metadata, but not yet proven for a runnable backend. A
Sharm To Go deployment must not merely hide Divers routes; it must be built and
migrated from the selected product composition. Packet 0R below is the first
implementation packet and blocks every catalog or booking packet.

## Packet map

Exactly one row may be active at a time. A later row cannot borrow unfinished
scope from an earlier row.

| Packet | Outcome | Default risk | Depends on |
| --- | --- | --- | --- |
| 0R | close Phase 0 review and enforce executable client composition | Tier 1 | owner resumes WEGO-010-A |
| 1A | catalog contract, domain, schema, ops API, and public API | Tier 1 | 0R |
| 1B | authenticated ERP catalog and content operations | Tier 1 | 1A |
| 1C | public SSR catalog on the website | Tier 2 unless auth/isolation changes | 1A |
| 1D | dedicated mobile shell and deterministic catalog snapshot | Tier 2 | 1A |
| 1E | owner-approved launch content and publication rehearsal | Tier 1 operational review | 1B–1D + real content |
| 2A | booking/PII/mobile-network security decisions | Tier 1 design packet | 1A–1D + owner policy inputs |
| 2B | availability, capacity, and booking backend | Tier 1 | 2A |
| 2C | staff booking operations | Tier 1 | 2B |
| 2D | website and mobile checkout/manage flows | Tier 1 | 2B–2C |
| 3A | provider-neutral payment core | Tier 1 | Phase 2 |
| 3B | Paymob hosted-checkout adapter | Tier 1 | 3A + sandbox contract |
| 3C | Fawry reference-code adapter, if commercially ready | Tier 1 | 3A + sandbox contract |
| 3D | refunds, cash-on-arrival, and CIB reconciliation | Tier 1 | activated payment methods |
| 4x | planner, saved/compare, reviews, then locale expansion | classify per packet | launch evidence |
| 5x | isolated deployment and controlled-launch gates | Tier 1 | all launch scope |

The packet names are a planning map, not pre-authorized board entries. Claude
must create only the next authorized entry and must preserve the board's
single-active-packet rule.

## Architecture decisions retained from Phase 0

### Product and client boundaries

Preserve `Platform → Product → Client Config → Client Deployment`:

- `products/travel-marketplace` owns reusable marketplace behavior.
- `clients/sharm-to-go` owns brand, locale, content, enabled features, and the
  deterministic release lock. Product code must not branch on the Sharm To Go
  client id.
- the Sharm To Go runtime gets its own database, Redis namespace or instance,
  secrets, storage, backup, release, and observability boundary. Database rows
  are not the primary client-isolation mechanism.
- no Travel Marketplace runtime or schema may couple to Resort OS.

### Website

Extend `web/apps/sharm-to-go-site`; do not rewrite the reviewed Phase 0 pages.
Reuse product-neutral Nuxt, locale, RTL/LTR, token, loading/error-state,
skip-link, accessibility, and test patterns from the Sharm Divers Club site.
Use Sharm To Go's own tokens and copy. Never copy Divers facts or imply live
availability before the booking backend exists.

The public catalog is server rendered for Arabic and English discoverability.
Public pages consume only the explicit public projection and show an honest
unavailable state on backend failure. A Phase 1 price is an approved catalog
"from" price and basis, not a slot quote or availability claim.

### Dashboard

Extend `web/apps/sharm-to-go-erp`; the Phase 0 readiness dashboard remains.
Reuse typed API/error and permission-gated UI patterns only after the auth
decision in Packet 1B. Client-side permission checks are UX only; every server
operation is authorized independently. Provider contact details and future
booking PII never appear in list projections unless the use case requires them.

### Mobile

Build a dedicated KMP module `mobile/apps/sharm-to-go` plus installable Android
module `mobile/apps/sharm-to-go-android`, mirroring only the proven target and
Compose wiring from the existing customer app. Reuse `mobile/shared` only for
invariants proven to be client-neutral. Use Sharm To Go tokens, package id,
copy, navigation, and store identity.

Do **not** hand-copy published services into Kotlin source. Packet 1D defines a
versioned `PublicCatalogSnapshotV1` JSON contract generated deterministically
from the same public catalog projection as the website. Its envelope records a
schema version, source revision, source-revision timestamp, locale coverage,
canonical payload, and SHA-256 digest of that payload. It does not inject the
current wall clock into reproducible bytes. CI uses synthetic fixtures; a
release bundle uses only an approved export. The app shows the snapshot's
freshness and an honest empty state.

Live mobile networking is an explicit Packet 2A decision rather than an
implementation-time surprise. The decision covers HTTP/serialization,
timeouts, cancellation, idempotent retries, certificate/TLS expectations,
native secret storage, telemetry redaction, and offline behavior.

### Repository reuse map

Claude must inspect these sources at the start of the packet that uses them.
They are reference implementations, not copy targets.

| Concern | Read first | Reuse | Do not copy |
| --- | --- | --- | --- |
| Public locale/RTL | `web/apps/sharm-divers-club-site/app/composables/useSiteLocale.ts`, its `app/content/locales.ts`, and `app/app.vue` | locale persistence, `dir`/`lang`, skip-link and focus structure | strings, brand values, Divers routes or facts |
| Honest remote states/cards | `web/apps/sharm-divers-club-site/app/components/ConditionsWidget.vue` and `OfferingCard.vue` | loading/error/unavailable discipline and accessible card structure | conditions endpoint, offering semantics, photos or prices |
| ERP API/UI tests | `web/apps/erp/app/composables/useDiversApi.ts` and `web/apps/erp/test/Divers.spec.ts` | typed errors, operation boundaries, permission and state test style | the monolithic file, Divers DTOs/routes, or its current bearer storage as an implicit auth decision |
| KMP shell | `mobile/apps/customer/src/commonMain/kotlin/com/wego/mobile/customer/` and its `WegoCustomerRoot.kt`, `design/SdcTokens.kt`, `design/SdcCard.kt`, `nav/AppDestination.kt`, and `state/AppLocaleState.kt` | target wiring, Compose theme/navigation/state injection and UI-test structure | SDC branding, WhatsApp inquiry behavior, manual catalog data or client-specific copy |
| Shared locale | `mobile/shared/src/commonMain/kotlin/com/wego/mobile/shared/locale/LocaleStore.kt` | only the already product-neutral locale-store contract | promotion of a second-client need to `mobile/shared` before its invariant is verified |
| Backend layering | representative `products/divers` domain/application/jOOQ/API files | typed ids, framework-free domain, operation services, jOOQ and HTTP test conventions | Divers aggregate names, transitions, permissions, schema or client meaning |
| Booking race proof | `products/divers/src/main/kotlin/com/wego/divers/domain/BookingFingerprint.kt`, `BookingPricing.kt` beside it, and `platform/application/src/test/kotlin/com/wego/divers/BookingCapacityConcurrencyIntegrationTest.kt` | fingerprint/idempotency and real PostgreSQL concurrency techniques after re-deriving marketplace rules | authenticated-actor assumptions, capacity rules, payment state or customer fields |

## Cross-cutting contracts

These requirements apply to every packet that touches the relevant surface.

### API and persistence

- Define or amend OpenAPI before implementing consumers. Public and operations
  endpoints use visibly separate paths, for example
  `/api/v1/travel-marketplace/public/...` and
  `/api/v1/travel-marketplace/ops/...`.
- Generate or mechanically verify consumer types where practical. Do not keep
  three unrelated hand-written representations of the same contract.
- The domain remains independent of Spring, HTTP, jOOQ, Redis, and provider
  SDKs. Application services own repository-backed policies; jOOQ is the
  primary persistence path.
- Use Flyway only. Never edit an already-applied migration. Every migration is
  tested both on a fresh database for the selected composition and as an
  upgrade from the latest supported schema history.
- Mutations that can race use optimistic version checks or database locks as
  appropriate. Return explicit conflict errors; never silently lose a staff
  edit.
- Validate and bound page size, filters, slugs, text length, money scale,
  durations, participant counts, and upload metadata. Escape rendered content
  and test stored/reflected XSS paths.

### Security and privacy

- Default deny. Public access is limited to the documented read-only public
  methods; operations routes require least-privilege product permissions.
- Product migrations may define product-neutral permissions and roles such as
  a Travel Marketplace operations role. Client-local account assignment is a
  deployment/bootstrap concern. Do not grant ordinary staff `platform-admin`
  or seed a Sharm-branded role in reusable product code.
- Use same-origin browser topology and exact trusted origins. Do not solve
  integration with permissive CORS.
- Secrets and raw provider credentials never enter Git, logs, analytics,
  screenshots, test snapshots, or client bundles.
- Correlation ids and audit records must not copy request bodies or PII.
  Sensitive list projections are minimal; detailed access is permission-gated
  and auditable.
- Accessibility, Arabic/English directionality, performance budgets, SEO, and
  analytics redaction are acceptance criteria in each UI packet, not work
  postponed entirely to launch week.

### Content and publication

Catalog aggregate state and translation state are separate:

- aggregate: `DRAFT → REVIEW → APPROVED → PUBLISHED`, with `SUSPENDED` and
  `ARCHIVED` transitions;
- each locale: `MISSING → DRAFT → REVIEWED → PUBLISHED`, with a source revision
  and `STALE` when approved source content changes.

Publication readiness is a repository-backed application policy, not a single
aggregate method pretending it can see other aggregates. It verifies active
category, approved provider, at least one valid option, required Arabic and
English content, catalog price/basis, duration/capacity wording, pickup,
versioned cancellation wording, fulfilment disclosure, and rights-cleared
media. The domain transition receives a validated readiness value and still
guards legal state transitions.

Media records contain asset id, source/owner, allowed channels, territory and
expiry when applicable, people-consent evidence, locale alt text, focal point,
and workflow state. Public projections expose the asset and public alt text,
never rights evidence or provider contacts.

Slugs are locale-aware, unique, and retain redirect/history behavior after a
published URL changes. Draft, suspended, and archived services are not
discoverable through the public API, sitemap, structured data, or snapshot.

## Packet 0R — executable composition and Phase 0 close-out

### Required work

1. Complete the outstanding independent review of the existing WEGO-010-A
   foundation and remediate every blocking finding.
2. Write an ADR comparing composition mechanisms. The preferred shape is:
   product code and product migrations become explicit build dependencies; a
   deterministic composition task reads one checked-in `release.lock.json`,
   selects exactly its product modules and migration locations, and embeds the
   lock plus digest in the resulting artifact. A different mechanism is valid
   only if it proves an equally strong fail-closed boundary.
3. Preserve migration history. Do not rewrite V3–V8. Test the chosen migration
   layout against both a fresh database and an existing Divers schema history.
4. Make startup fail on an unknown module, stale lock, artifact/lock mismatch,
   missing migration location, or accidental multi-client configuration.
5. Expose only non-sensitive build/composition metadata in health/info: client
   id, product ids, lock schema version, artifact revision, and lock digest.
6. Update Foundry validation so a metadata-valid but non-buildable composition
   fails CI.

### Exit evidence

- a fresh Sharm To Go database has platform and Travel Marketplace migrations
  only; it has no Divers tables, permissions, or seed data;
- its application context and route inventory contain no Divers controller or
  product bean;
- a fresh Sharm Divers Club composition contains no Travel Marketplace table,
  permission, route, or bean;
- both artifacts are reproducible from their checked-in locks and boot against
  real throwaway PostgreSQL instances;
- lock tampering and wrong-artifact startup tests fail closed;
- Foundry validation, repository checks, build/tests, and the independent Tier
  1 review are clean.

No catalog migration starts until all of those statements are executable
evidence rather than documentation.

## Packet 1A — catalog backend and contracts

### Domain and schema

Implement `Category`, `Provider`, `Service`, `ServiceOption`,
`ServiceTranslation`, `CategoryTranslation`, and `ServiceMedia` with typed ids,
explicit lifecycle methods, timestamps, audit actor, and optimistic version.
Keep dated availability and inventory out of this packet.

`ServiceOption` owns duration, participant bounds, approved EGP catalog price,
and price basis. It does not promise that a date is available or that the
catalog price is the final checkout quote. `Provider` distinguishes fulfilment
model and operational approval; internal contact data is never public.

Add product-namespaced permissions for public-independent operations such as
catalog view/manage, content review/publish, provider view/manage, and media
review. Grant least privilege to a product-neutral operations role; use a
separate bootstrap step for actual Sharm To Go users.

### APIs

- public category and service list/detail: published records only, bounded
  pagination/filters, locale fallback made explicit, stable cache validators,
  and no internal provider/media fields;
- operations category/service/provider/media list/detail/create/update and
  lifecycle actions with explicit permissions and version preconditions;
- publication-readiness endpoint returning machine-readable blocking reasons;
- deterministic public catalog export used by Packet 1D;
- OpenAPI schemas and errors committed with contract tests.

### Exit evidence

- transition and readiness rules have domain/application tests;
- repository and HTTP tests use real PostgreSQL/Testcontainers and include
  unauthenticated, wrong-permission, stale-version, invalid-transition,
  injection, pagination-bound, and non-published public-access negatives;
- fresh and upgrade migrations pass only in the selected Sharm To Go
  composition;
- a public response cannot expose provider contacts, rights evidence, staff
  audit notes, or unpublished translations;
- an empty database is a valid state and no production-looking content is
  seeded.

## Packet 1B — authenticated catalog operations dashboard

Before adding business screens, document and implement the browser session
boundary. Do not silently duplicate a bearer-token storage pattern. Prefer a
same-origin, secure, HttpOnly, SameSite session/BFF with CSRF protection; if a
different mechanism is selected, the Tier 1 record must state token lifetime,
XSS exposure, refresh/logout behavior, CSP, and why the residual risk is
accepted before any provider contact or booking PII is displayed.

Build login/logout/session-expiry and route guards, then service list/editor,
publication readiness, locale status, media-rights workflow, and minimal
provider operations. Destructive or publication actions require confirmation;
permission-hidden controls remain backed by real server authorization. Include
loading, empty, conflict, validation, forbidden, expired-session, and backend-
unavailable states in Arabic and English.

Exit requires component tests, API-contract tests, an authorization matrix,
CSRF/XSS-focused tests for the selected session model, and an end-to-end staff
journey using a least-privilege account.

## Packet 1C — public SSR catalog

Turn `/experiences` into the real category/filter/list page and add one
canonical localized service-detail route consistent with
`design/INFORMATION_ARCHITECTURE.md`. Keep `/booking-preview` as a clearly
labelled prototype; it is not checkout.

Render only the public projection. Show the catalog price as "from" plus its
basis and say that date-specific price/availability is confirmed during
booking. Do not add a dead Book button; use an honest pre-booking/contact state
until Phase 2. Include canonical/hreflang, sitemap exclusion for non-published
records, structured data based only on approved facts, cache/error behavior,
keyboard/focus/RTL checks, and public-content analytics with no PII.

Exit requires SSR tests, public-contract tests, Arabic/English content parity,
accessibility checks, bounded performance evidence, and proof that draft or
suspended slugs return the agreed non-discoverable response.

## Packet 1D — dedicated mobile catalog

Create the KMP and Android application modules only after the owner approves a
stable application id. Brand artwork may remain an honest placeholder until
store preparation, but no placeholder app identity may reach a release build.

Implement Home, Experiences list/filter, and Service Detail using the same
public contract through `PublicCatalogSnapshotV1`. Add a deterministic export
and packaging task; do not edit generated service records by hand. The app
validates schema version and digest, supports Arabic/English and RTL/LTR,
survives empty/corrupt snapshots without fabricated content, and states the
snapshot revision/freshness in diagnostics.

Exit requires JVM Compose UI tests, Android build/install evidence, snapshot
contract and determinism tests, locale parity, and a comparison proving the
packaged public ids/revision match the reviewed export. iOS framework build is
required where the existing environment supports it; store submission remains
a later authorized packet.

## Packet 1E — real content and publication rehearsal

This is where approved owner content enters a non-production review environment.
Engineering packets 1A–1D may use synthetic fixtures and must not wait for
business copy, but Phase 1 does not exit until at least one complete real
service passes source, translation, provider, policy, price, and media-rights
review.

Exercise draft → review → approved → published → suspended across dashboard,
public SSR, API, and a regenerated mobile snapshot. Record who approved every
fact and verify suspension removes the service from every public channel. This
packet never turns missing facts into defaults. It blocks real publication and
any external pilot, but missing launch content does not by itself block
owner-authorized engineering packets 2A–2D from continuing with synthetic test
fixtures after 1A–1D are complete.

## Packet 2A — booking and privacy decisions

Complete these decisions before the first booking/PII migration:

- data classification, minimum guest fields, lawful purpose/consent wording,
  encryption/backup treatment, access matrix, retention periods, anonymization
  behavior, and support export/deletion process;
- exact confirmation modes for launch: `INSTANT` and `STAFF_REVIEW`. Defer
  `ON_REQUEST`/quote negotiation until a separate quote/acceptance state model
  exists; do not overload `NEW` with an informal quote workflow;
- capacity reservation/expiry rules, client timezone and DST behavior, and
  cancellation-policy versioning;
- guest management capability: public reference is display-only, never
  authorization. Use a high-entropy one-time-delivered management secret stored
  only as a hash, scoped/revocable, and never logged. On web, deliver it in the
  URL fragment, consume it into session state, and immediately remove it from
  the visible URL; send it to the API in a dedicated header. On mobile, store it
  in platform secure storage. Do not build reference+email/phone lookup until a
  rate-limited OTP or equivalent verified recovery channel exists;
- the KMP network stack and serialization, retry, timeout, offline, telemetry,
  and secure-storage contracts. Booking commands may retry only with the same
  stable idempotency key and payload fingerprint.

The packet exits with reviewed ADRs, threat model, data-flow map, endpoint
contract, retention decision, and test plan. "Decide while implementing" is
not an acceptable exit.

## Packet 2B — availability, capacity, and booking backend

Implement dated slots and a capacity ledger/reservation model rather than a
single mutable counter. Store UTC instants plus the business timezone/context
needed to reproduce customer-visible local time. A `NEW` staff-review booking
holds capacity until its explicit expiry; an instant-confirmation booking
reserves and confirms atomically.

The immutable booking snapshot contains service/option/provider/pickup facts,
guest counts, locale, exact money/currency/basis, cancellation-policy version,
confirmation mode, and customer-visible local schedule. Revalidate publication,
slot, capacity, and price inside the transaction.

Guest create is rate-limited and idempotent by unique key plus request
fingerprint; concurrent requests serialize with the proven database-locking
pattern. Management endpoints require the hashed capability contract from 2A.
Write domain events to the transactional outbox, but do not pretend an email,
WhatsApp, or other dispatcher exists before its own authorized integration.

Exit requires real PostgreSQL concurrency tests for last-place contention,
duplicate idempotency, mismatched fingerprints, expiry races, staff confirm vs
cancel, timezone boundaries, stale prices, and cross-composition negatives.
PII must not appear in list DTOs, logs, metrics, traces, or error bodies.

## Packet 2C — staff booking operations

Add least-privilege booking queue, calendar/capacity editor, detail, internal
notes, confirm, complete, cancel, and expire actions. List/roster projections
contain only operationally necessary fields; full contact detail requires its
own permission and audit event. Financial-looking controls remain absent until
Phase 3.

Exit requires the full role/action matrix, stale-version and concurrency UI
handling, sensitive-field tests, audit evidence, and a real end-to-end
staff-review booking lifecycle.

## Packet 2D — website and mobile checkout/manage

Implement the documented step order: slot → guests/language/add-ons → pickup
and minimum contact → immutable price/policy review → confirmation result.
Payment is explicitly "not collected online" until an activated Phase 3 method
exists; no fake card form or paid state appears.

Both clients use the same booking contract, stable idempotency key, capability
management model, and honest retry/recovery states. Website and native storage
must follow Packet 2A. No customer account system or returning-customer token is
implied by guest checkout.

Exit requires web and Compose UI tests, API consumer contract tests, duplicate
submit/network-timeout recovery, capability leakage checks, accessibility and
RTL checks, and end-to-end instant and staff-review journeys.

## Phase 3 — payment packets

### Packet 3A: provider-neutral core

Model payment intent, attempt, method, immutable amount/currency, provider
reference, refund, and independent payment state. Define hosted-redirect,
webhook, provider-query, cash-on-arrival, and reconciliation ports without a
gateway SDK. A booking is not paid because the browser returned successfully.

### Packet 3B: Paymob

Activate only with real sandbox credentials and signed merchant terms. Use a
provider-hosted checkout, fixed allow-listed endpoints, raw-body signature
verification, unique provider event ids, amount/currency/merchant/order checks,
out-of-order transition handling, replay tests, provider query reconciliation,
redacted payload logging, and one database transaction plus outbox event.

### Packet 3C: Fawry

Implement only if its commercial and sandbox inputs are ready. Reference-code
expiry, callback verification, status query, replay, and amount/currency/order
matching receive the same Tier 1 evidence as Paymob. It is optional, not a
reason to block a Paymob-only controlled launch.

### Packet 3D: refund, cash, and reconciliation

Refunds require separate permissions, reason, idempotency, audit, provider
query/reconciliation, and explicit partial/full rules. Cash-on-arrival is a
service-level collection method and never creates an online-paid state. CIB is
the settlement account/reconciliation source described in
`design/PAYMENT_FOUNDATION.md`; do not invent a gateway integration or infer a
provider payout/commission from public price. Provider settlement and
commission remain deferred until real agreements require them.

Every payment packet is Tier 1 and requires sandbox evidence, negative and
replay tests, secret-redaction evidence, and independent review before the next
one. No production credential, live transaction, or activation follows from
this plan.

## Phase 4 — post-launch product packets

Split planner, saved/compare, reviews, and each new locale into separate board
packets. The planner can rank only currently published facts and availability;
it cannot invent an itinerary fact. Any AI-assisted ranking requires a
separate owner decision, bounded output contract, and review. A review requires
an eligible completed booking at the domain layer. Add Russian or another
locale only after measured Arabic/English parity and human approval. Add
currency display only after source, rounding, refresh, and expiry policy.

## Phase 5 — isolated deployment and controlled launch

Launch work is also packetized; it does not become one final catch-all:

1. provision the isolated database, Redis, secrets, storage, backups, and exact
   Sharm To Go artifact from its release lock;
2. prove backup and restore, migration rollback/recovery procedure, lock digest
   and artifact provenance, and zero Divers route/table/permission exposure;
3. add SLOs, health checks, correlation, alerts, dashboards, no-PII logging,
   support access, incident/provider-outage/payment reconciliation runbooks;
4. run content, accessibility, Arabic/English, SEO, analytics-redaction,
   performance, security, privacy/retention, and launch smoke gates;
5. prepare store assets and signed builds in a separate authorized release
   packet. iOS submission requires the documented Mac/CI signing path.

A deployment or launch requires explicit owner authorization separate from
implementation completion.

## Owner decision gates

1. **Resume and priority:** authorize WEGO-010-A only after the current active
   packet has actually closed; Packet 0R is first.
2. **Real launch content:** provide completed
   `design/SERVICE_CONTENT_TEMPLATE.md` records, approvals, Arabic/English
   translations, real prices/policies, and rights-cleared assets. Synthetic
   fixtures let engineering proceed through 1D but cannot satisfy 1E.
3. **Staff access:** name the first dashboard users and approve the
   least-privilege Travel Marketplace operations role/permission matrix.
4. **Mobile identity:** approve the stable application id and public app name
   before Packet 1D. Final icon/store artwork is required only before a release
   build, not before domain/backend work.
5. **Privacy:** approve minimum guest fields, consent wording, retention and
   anonymization before Packet 2B stores PII.
6. **Booking policy:** approve launch confirmation modes, hold expiry,
   cancellation rules, timezone, pickup model, and recovery channel before 2B.
7. **Payments:** provide signed merchant agreements, legal merchant identity,
   settlement details, sandbox credentials, and official webhook/signature
   specifications only for each adapter being activated. Secrets never enter
   this repository.

## Standard completion evidence for every packet

Claude's handoff must include:

- board packet id/status and exact scope completed;
- changed files and architecture decisions;
- `git diff --check` and `bash scripts/repository-check.sh` results;
- relevant backend, web, mobile, Foundry, migration, contract, and live-boot
  command results, including the diagnostic regression that fails without the
  fix;
- security/permission negatives and real PostgreSQL evidence where relevant;
- independent review result for Tier 1 work and confirmation that all blocking
  findings are resolved;
- residual risks, deferred work, and exact owner input needed next;
- explicit confirmation that no real data was invented, no secret was written,
  and no commit, push, deployment, or production action occurred unless each
  was separately authorized.

The immediate next executable outcome is Packet 0R, not the catalog schema.
