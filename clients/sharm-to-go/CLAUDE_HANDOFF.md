# Sharm To Go — technical continuation handoff

Status refreshed: 2026-09-30 (reactivation and reorganization).

> **Superseded pointer:** [`ROADMAP_AR.md`](ROADMAP_AR.md) is now the
> canonical status/order source (same pattern as Safari's own
> `ROADMAP_AR.md`). This file remains implemented history and technical
> detail, not the current plan. The owner explicitly reactivated WEGO-010 on
> 2026-09-30.

## Start here

1. Read the [dated agent checkpoint](handoff/2026-09-29_NEW-AGENTS_START-HERE.md)
   and [multi-account workflow](handoff/CHATGPT_AND_CLAUDE_MULTI_ACCOUNT_WORKFLOW.md)
   before this technical history.
2. Read [`delivery/README.md`](delivery/README.md) and
   [`delivery/00_CURRENT_STATE.md`](delivery/00_CURRENT_STATE.md), then read
   `AGENTS.md`, `docs/ENGINEERING_CONSTITUTION.md`, and the WEGO-010-A
   section of `docs/execution/WEGO_EXECUTION_BOARD.md`.
3. Run `git status --short --branch` and `git worktree list`. Do not assume the
   checkout at `/home/wego/wego-platform` contains the latest Sharm To Go code.
4. The preserved continuation implementation is on branch
   `worktree-wego-010a-0r-isolation` in the existing isolated worktree. If that
   branch has not yet been integrated, do not reimplement its work on `main`.
5. While the packet is paused, inspection and reporting are allowed but product
   implementation is not. After explicit reactivation, run
   `bash scripts/sharm-to-go-check.sh` before claiming the branch is green.

## Implemented on this branch

- Packet 0R: a separate `platform/apps/sharm-to-go` Spring Boot application,
  product classpath, permission catalog, and Flyway location. Divers product
  code and Divers migrations are absent from this application.
- Packet 1A: provider/category/service catalog domain, V3 migration, staff CRUD
  and publication lifecycle, public projection, audit, permissions, and the
  dedicated `sharm-to-go-api.yaml` contract.
- Packet 1B: authenticated Sharm To Go ERP pages for providers, categories,
  services, and service lifecycle actions.
- Packet 1C: public `/experiences` list/filter and `/experiences/:id` detail
  backed by same-origin Nitro proxy routes and the real public catalog API.
- Packet 1D: `mobile/apps/sharm-to-go` (KMP, jvm/android/iosArm64/
  iosSimulatorArm64) and `mobile/apps/sharm-to-go-android` (installable app),
  mirroring `mobile/apps/customer`'s proven pattern. Home + Experiences list/
  filter + Experience detail screens, real ported website copy, the
  already-approved `favicon.svg` mark as the launcher icon. Catalog is bundled
  from `mobile/shared`'s `TravelCatalogSnapshot` (versioned, refreshed per
  release, not live-fetched — no KMP HTTP client exists yet, deliberately
  deferred to the real booking phase) and is honestly empty, matching the real
  backend's current state. A real debug APK builds; class-string inspection of
  its dex files confirms zero Divers/customer classes.

- Packet 1E/research follow-through: the 37 service concepts now carry
  owner-directed Sharm To Go operation, EGP pricing, capacity, schedule, safety,
  inclusions and cancellation decisions in the intake material. This is a
  content/operations baseline, not proof of live availability or photo rights.
- Marketing repositioning: `BRAND_AND_GROWTH_STRATEGY.md` and
  `CONVERSION_DELIVERY_PLAN.md` supersede the old customer-facing
  multi-provider-marketplace message. The website now has the approved story,
  About/FAQ/Contact/Privacy/Terms routes and a real WhatsApp contact path.

No production deployment, production customer data, live payment or external
publication has occurred. Rights-cleared real photography and production
availability remain launch gates.

## Next scope after explicit reactivation

No implementation packet is authorized today. Once the owner explicitly
reactivates WEGO-010, the next planned implementation packet is the
request/booking foundation in
[`delivery/01_REQUEST_AND_BOOKING.md`](delivery/01_REQUEST_AND_BOOKING.md): one
durable public request/reference, safe lifecycle and immutable service/price
snapshot, followed by the ERP queue, website conversion flow and mobile
integration in checklist order. It must be implemented once in the backend and
consumed by all three surfaces; WhatsApp stays a contextual human channel and
never becomes the confirmation authority.

The owner subsequently requested text-only research from Egyptra's Sharm
listings. Start at `clients/sharm-to-go/content-research/README.md`. The earlier
24-card private subset became 14 consolidated concepts. The later broad audit
checked five result pages (123 returned cards, 122 unique source slugs), mapped
the repeated listings into 23 additional concepts, and documented deliberate
holds for dolphin experiences and for diving that belongs in the Sharm Divers
Club ownership path. The 37 concepts began as `RESEARCH_ONLY`; subsequent owner
decisions supplied Sharm To Go EGP prices and operating parameters in the
intake sheets. Competitor EUR snapshots, source images, reviews and copied long
descriptions remain non-publishable references. A completed intake row still
does not prove live date availability or image rights.

Before any store release of the mobile app, the owner must separately confirm
the release identity is final (Play Store listing name, `applicationId`
`com.wego.mobile.sharmtogo`, and the launcher icon) — engineering work does
not block on this, but a store submission does.

Do not bundle payment, Dining, Accommodation, Car Rental or social automation
into the request foundation. Their separate gates are in `delivery/`.

## Repository integration warning

The earlier Sharm To Go packets have been integrated through multiple reviewed
pull requests, but this worktree remains the continuation checkout and can move
ahead of its remote branch. Always inspect `git status`, `git branch -vv` and
`origin/main...HEAD`; preserve unrelated work and never assume a merge/push or
deployment is authorized by a documentation update.

## Required toolchains

- JDK 25. The current machine has Temurin at
  `/home/wego/.jdks/temurin-25.0.3+9`.
- Node 24.19.0 from the root `.nvmrc`. It is installed on this machine at
  `/home/wego/.local/share/nodejs/node-v24.19.0-linux-x64` and exposed as
  `/home/wego/.local/bin/node`.
- pnpm 10.34.4 from `web/package.json`.
- An Android SDK for the `mobile/apps/sharm-to-go*` modules (needed since
  Packet 1D). Set `ANDROID_HOME`/`ANDROID_SDK_ROOT`, or write
  `sdk.dir=<path>` to a local, gitignored `local.properties` at the repo
  root — this machine's SDK is at `/home/wego/android-sdk`.

Do not accept results from Node 20: current ESLint dependencies use
`Object.groupBy`, so lint fails before inspecting project code on that runtime.

## Quality gate

Run from the repository root:

```bash
bash scripts/sharm-to-go-check.sh
```

The gate checks the Sharm To Go backend, the existing Divers backend regression
surface, both Sharm To Go web apps, `mobile/shared` plus both Sharm To Go
mobile modules (and, as a Divers regression check, `mobile/apps/customer`
and `-android`), Foundry/OpenAPI, repository invariants, and whitespace.
Record exact results in the execution board before closing or starting
another packet.
