# Sharm To Go — continuation handoff

Status refreshed: 2026-09-03 (Packet 1D added).

## Start here

1. Read `AGENTS.md`, `docs/ENGINEERING_CONSTITUTION.md`, and the WEGO-010-A
   section of `docs/execution/WEGO_EXECUTION_BOARD.md`.
2. Run `git status --short --branch` and `git worktree list`. Do not assume the
   checkout at `/home/wego/wego-platform` contains the latest Sharm To Go code.
3. The current implementation is on branch
   `worktree-wego-010a-0r-isolation` in the existing isolated worktree. If that
   branch has not yet been integrated, do not reimplement its work on `main`.
4. Run `bash scripts/sharm-to-go-check.sh` before claiming the branch is green.

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

No production deployment, production data, external publication, live payment,
or real service seeding has occurred.

## Next scope

The next planned implementation packet is 1E: a real, owner-approved launch
service published through the real workflow (Provider -> Category -> Service
-> options/media -> submit-for-review -> approve -> publish), proving every
surface (backend, ERP, website, mobile) renders one real record identically.
That needs at least one real service record with approved Arabic/English
copy, price/policy/provider facts, and rights-cleared media. Synthetic
fixtures may be used in engineering tests but must never be presented as real
inventory.

Before any store release of the mobile app, the owner must separately confirm
the release identity is final (Play Store listing name, `applicationId`
`com.wego.mobile.sharmtogo`, and the launcher icon) — engineering work does
not block on this, but a store submission does.

Do not start booking/availability, payment, Dining, Accommodation, Car Rental,
or WhatsApp automation inside Packet 1E. Those are separate later packets.

## Repository integration warning

At this handoff the isolated branch has seven Sharm To Go commits based on
main commit `4dbcb38`. The remote main line also contains later WEGO-012/013 work,
and the primary worktree may contain local planning edits. Inspect both sides
and preserve every unrelated change before any merge, rebase, or cherry-pick.
Do not delete the isolated worktree or branch before its commits are integrated
and verified. Commit, merge, push, and deployment still require explicit owner
authorization.

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
