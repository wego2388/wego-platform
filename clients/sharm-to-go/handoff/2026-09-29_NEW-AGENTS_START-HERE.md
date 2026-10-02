# Sharm To Go — new Codex and Claude checkpoint

Snapshot date: 2026-09-29

This is the first file a new account reads. It records the checkout boundary,
what is real, what is missing and the safe resume order. It does not authorize
implementation by itself.

## 1. Stop condition: the mission is paused

WEGO-010 is currently `PAUSED`. The active repository mission is WEGO-016
(Safari Tours Sharm). Until the owner explicitly pauses or completes WEGO-016
and reactivates WEGO-010:

- inspect and report only;
- do not write product code or migrations;
- do not mark future delivery boxes complete;
- do not merge, rebase, cherry-pick, commit, push or deploy;
- do not touch production, DNS, secrets, payment providers or customer data.

An instruction to "read the project" or "continue the handoff" is not mission
activation. Activation must name WEGO-010 and be reflected in the current
execution board with exactly one matching `ACTIVE` packet.

## 2. Exact preserved checkout

| Item | Snapshot value |
|---|---|
| Repository | `/home/wego/wego-platform` |
| Sharm To Go worktree | `/home/wego/wego-platform/.claude/worktrees/wego-010a-0r-isolation` |
| Branch | `worktree-wego-010a-0r-isolation` |
| Code/docs baseline HEAD | `aff1a27a14f0a1eebb834f156224a3c59301dd3d` |
| Remote Sharm branch | `origin/wego-010a-sharm-to-go` at `3a9a297f5a0927300297a2a07ec167dc5bcd52ac` |
| Remote main at inspection | `origin/main` at `b1b570049503031675b8ecb88a3acfee28096d75` |

The isolated branch has two commits not present on the remote Sharm branch or
on the inspected `origin/main`:

1. `4be891ef3862473c6659d8ab194c292ddfbd5500` — brand/marketing-and-booking
   alignment.
2. `aff1a27a14f0a1eebb834f156224a3c59301dd3d` — complete phased delivery
   workbook.

The branch and `origin/main` have diverged from their common base
`3a9a297f5a0927300297a2a07ec167dc5bcd52ac`. Do not assume that "latest main"
or the checkout at `/home/wego/wego-platform` contains these two commits. Do not
recreate them manually, and do not integrate them while WEGO-010 is paused.

This handoff/pause-alignment update is intentionally left as uncommitted
documentation work for the owner or the future active packet to review. Preserve
it; never clean or reset this worktree just to obtain a clean status.

## 3. What exists and has real implementation

- A separate Spring Boot application at `platform/apps/sharm-to-go`, with its
  own Flyway path and Travel Marketplace composition. Divers classes,
  permissions and tables were proven absent from the isolated runtime.
- Provider, category, service, option and media catalog domain with publication
  workflow, permissions, staff APIs, public projection and OpenAPI contract.
- A Sharm To Go ERP with provider/category/service management.
- A bilingual public site with real catalog list/detail routes and honest empty
  states.
- A dedicated KMP/Android mobile application with bundled catalog snapshot and
  a buildable debug APK.
- Approved brand direction, trust/contact pages and contextual WhatsApp path.
- Research/intake material for 37 service concepts. Owner-directed prices and
  operating assumptions exist in intake material, but they do not prove live
  date availability, supplier commitments or image rights.

The exact implementation evidence and caveats are in `../CLAUDE_HANDOFF.md` and
the WEGO-010-A board history. A prior independent Tier 1 review attempt was
interrupted; it found real issues that were fixed, and the owner explicitly
accepted self-verification to continue. Do not rewrite that history as a fully
completed independent review.

## 4. What does not exist yet

- Durable public travel request/booking persistence and a safe reference.
- Capacity/availability reservation and concurrency proof for confirmation.
- ERP request queue and lifecycle actions.
- A real website request result/status journey.
- Mobile networking, request submission and request tracking.
- Payment collection, refund or financial reconciliation.
- Production hosting, DNS/TLS, production secrets, backup/restore proof or
  deployment.
- Published rights-cleared launch imagery and proven live availability.
- Dining, cars and accommodation implementations.

`/booking-preview` is a design prototype. It creates no booking or payment.
WhatsApp is a human contact channel, not system-of-record confirmation.

## 5. Files to trust, in order

1. Repository governance: `AGENTS.md`, engineering constitution, review and
   collaboration policies.
2. Current authorization: the live execution board plus the owner's current
   instruction.
3. Agent coordination: this file and
   `CHATGPT_AND_CLAUDE_MULTI_ACCOUNT_WORKFLOW.md`.
4. Delivery truth: `../delivery/README.md`, `00_CURRENT_STATE.md`, the active
   phase file, then `07_ACCEPTANCE_AND_EVIDENCE.md`.
5. Product truth: `../BRAND_AND_GROWTH_STRATEGY.md` and
   `../CONVERSION_DELIVERY_PLAN.md`.
6. Historical implementation detail: `../CLAUDE_HANDOFF.md`, execution plan and
   technical execution plan.
7. Research only: `../content-research/`. Never publish it as verified inventory
   without current operational and rights evidence.

If two files conflict, do not pick the more convenient claim. Prefer the newer
governance/checkpoint source, verify against executable code and Git, and record
the conflict before changing anything.

## 6. Future resume sequence — not authorized yet

When the owner explicitly reactivates WEGO-010, the first packet is a resume and
integration gate before new feature code:

1. Confirm WEGO-016 is paused or complete and WEGO-010 is the only active
   mission/packet in the current board.
2. Name one implementer and one independent reviewer. Only the implementer
   writes to the active worktree.
3. Inspect `git status --short --branch`, `git worktree list`, branch tracking,
   and both-direction commit ranges. Preserve every owner change.
4. Create a fresh, owner-approved integration worktree from the then-current
   base. Do not rebase the preserved historical worktree in place.
5. Reconcile the two local continuation commits with current main deliberately;
   audit conflicts across platform, contracts, migrations, web workspace,
   mobile settings and the execution board.
6. Run the existing Sharm To Go quality gate before feature changes and record
   exact failures as baseline evidence:

   ```bash
   bash scripts/sharm-to-go-check.sh
   ```

7. Revalidate application isolation and migration ordering against the current
   platform. Any migration, permission, PII or client-boundary change is Tier 1.
8. Only after the resume gate is green should the implementer scope the first
   unchecked product packet. The planned candidate is
   `delivery/01_REQUEST_AND_BOOKING.md`; it is not automatically authorized.

## 7. Non-negotiable implementation boundaries

- PostgreSQL is durable truth; website, mobile, WhatsApp and automation never
  become separate booking authorities.
- One request reference and one immutable commercial snapshot are shared by
  web, ERP and mobile.
- Public writes require validation, idempotency, row locking, privacy-safe
  lookup and real concurrency tests.
- A request is not a confirmed booking. Payment state is independent from
  request receipt.
- No invented availability, ratings, reviews, suppliers, photo rights, service
  history or customer claims.
- Sharm Divers Club remains a separate product/client/application/schema.
- Payment, Dining, Cars, Stays and automation are separate gated packets, not
  convenient additions to the request foundation.

## 8. First prompt for the new Claude account

```text
اقرأ الملف كاملًا أولًا:
clients/sharm-to-go/handoff/2026-09-29_NEW-AGENTS_START-HERE.md

ثم اقرأ ملفات الحوكمة والترتيب المذكورة داخله وافحص Git والـworktrees قراءة فقط.
WEGO-010 متوقف حاليًا، لذلك لا تكتب كودًا ولا تعمل merge/rebase/commit/push/deploy.
أعطني تقريرًا مختصرًا يؤكد: المسار والفرع والـHEAD، الفرق عن remote/main، ما هو
منفذ فعليًا، وما هي أول بوابة استئناف آمنة. انتظر تفعيل محمد الصريح قبل التنفيذ.
```

## 9. First prompt for Codex

```text
Read clients/sharm-to-go/handoff/2026-09-29_NEW-AGENTS_START-HERE.md completely,
then follow its mandatory reading order. Inspect the preserved worktree and Git
divergence read-only. WEGO-010 is paused: do not implement, integrate, commit,
push or deploy. Report any contradiction between docs, code and Git, then wait
for explicit owner reactivation. When reactivated, act as the independent Tier
1 reviewer unless the owner assigns you as implementer for a named packet.
```

