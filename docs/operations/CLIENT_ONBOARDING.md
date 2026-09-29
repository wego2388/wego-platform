# Client onboarding

How to add a new client to Wego Platform. Written from the three real clients
that exist today (`clients/sharm-divers-club`, `clients/sharm-to-go`, and
`clients/safari-tours-sharm`) — every
step below is what actually happened for at least one of them, not a
theoretical process.

## What "a client" currently means here

A client is a directory under `clients/<client-id>/` containing a declarative
`client.manifest.json` (validated against
`foundry/schemas/client-manifest.schema.json`) and a generated
`release.lock.json` plus `release.plan.json`. Foundry (`foundry/`) auto-discovers every directory
under `clients/` and validates it — there is no separate registration step.

`client.manifest.json` takes exactly one release product id. That product may
declare several operational modules (for example the Wego Divers release owns
Divers, HR, Accounting and Payroll), but its generated release plan selects one
physically isolated backend application and one database migration set. Safari
Tours Sharm and Sharm To Go have separate backend projects; unrelated product
classes and tables are tested absent rather than hidden with feature flags.

## Steps

1. **Pick a `clientId`.** Lowercase, hyphenated, matches
   `^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$` (e.g. `sharm-divers-club`). This becomes
   the directory name under `clients/`.

2. **Create `clients/<client-id>/client.manifest.json`:**

   ```json
   {
     "schemaVersion": 1,
     "clientId": "<client-id>",
     "displayName": "<Human-readable name>",
     "product": {
       "id": "wego-divers",
       "version": "0.1.0"
     },
     "organization": {
       "timezone": "Africa/Cairo",
       "defaultLocale": "en",
       "supportedLocales": ["ar", "en"],
       "currency": "EGP"
     },
     "experienceProfiles": ["STANDARD"],
     "deploymentIsolation": "ISOLATED_INSTANCE"
   }
   ```

   `product.id` must be one of the ids in `products/*/product.manifest.json`
   (currently `wego-divers`, `wego-hr`, `wego-accounting`, `wego-payroll`,
   `wego-travel-marketplace`). `deploymentIsolation` must stay
   `ISOLATED_INSTANCE` — it is the only value the schema accepts today.

3. **Create `clients/<client-id>/README.md`.** State what's real and what
   isn't yet — both existing clients' READMEs do this explicitly (no fake
   services, no invented pricing, explicit "not deployed yet" callouts where
   true). Copy `clients/sharm-divers-club/README.md` as the minimal shape, or
   `clients/sharm-to-go/README.md` if the client also has web apps and design
   docs to link.

4. **Add an executable release profile.** Add one constrained entry to
   `foundry/catalog/release-profiles.json`. Every referenced Gradle project,
   package, Dockerfile, contract, Compose bundle and environment example must
   already exist. This trusted catalog is repository engineering input, never
   client-authored configuration.

5. **Generate the release files.** From `foundry/`:

   ```bash
   pnpm install --frozen-lockfile
   pnpm run generate:release
   ```

   This writes both generated files. They are deterministic and contain no
   timestamps. Do not hand-write them.

6. **Validate.** Still from `foundry/`:

   ```bash
   pnpm run validate
   ```

   This resolves the new client against the module catalog and rejects it if
   the product id is wrong, the lock and backend composition differ, a path is
   missing, Compose selects different services, or a manifest has an unknown
   property. Fix and re-run `generate:release` until this is clean.

7. **Prove isolation.** Add application tests that boot a fresh PostgreSQL,
   assert the exact Flyway version list, and assert foreign product tables and
   controller classes are absent. CI also scans the built bootJar. A navigation
   toggle or permission check is not isolation proof.

8. **Confirm nothing else broke.** `bash scripts/repository-check.sh` from
   the repo root still needs to pass (it doesn't touch clients directly, but
   catches unrelated repository-invariant regressions). Run the full
   `contracts` CI job's steps locally if in doubt.

9. **If the client needs its own web app(s)**, follow the existing
   `web/apps/sharm-to-go-site`/`sharm-to-go-erp` or
   `web/apps/sharm-divers-club-site` as the real precedent for wiring a new
   Nuxt app into the `web/` pnpm workspace — there is no scaffolding command
   for this yet; it's still copy-and-adapt.

## What this process does not do

- **It does not deploy anything.** Onboarding a client here only makes it
  pass Foundry's own validation — see
  `docs/execution/WEGO_EXECUTION_BOARD.md` and the platform's own README for
  the current state of production deployment (there isn't one yet for any
  client).
- **It does not provision a VPS or database.** It creates and verifies the
  executable release definition; an operator still follows the isolated-client
  runbook and supplies secrets on the target.
- **It does not grant deploy authority.** Generated release files are evidence,
  not approval to push, deploy, change DNS, or use external credentials.

## Real precedent, for comparison

- `clients/sharm-divers-club`: manifest + lock + a short README pointing at
  an external (non-repo) marketing reference. No client-specific web app of
  its own beyond the shared ERP; a separate `web/apps/sharm-divers-club-site`
  public site was built later, in its own phase of work.
- `clients/sharm-to-go`: manifest + lock + a full design/execution-plan
  document set (`PRODUCT_BLUEPRINT.md`, `SERVICE_OWNERSHIP.md`,
  `LOCALES_AND_CONTENT.md`, `REFERENCE_STUDY.md`, `EXECUTION_PLAN.md`,
  `TECHNICAL_EXECUTION_PLAN.md`) plus its own `web/apps/sharm-to-go-site` and
  `web/apps/sharm-to-go-erp` foundations, built in the same initial packet.

Which depth a new client needs is a real scoping decision for whoever opens
the packet, not something this doc prescribes — but the manifest/lock/README
steps above are the fixed, non-optional minimum either way.
