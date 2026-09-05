# Client onboarding

How to add a new client to Wego Platform. Written from the two real clients
that exist today (`clients/sharm-divers-club`, `clients/sharm-to-go`) — every
step below is what actually happened for at least one of them, not a
theoretical process.

## What "a client" currently means here

A client is a directory under `clients/<client-id>/` containing a declarative
`client.manifest.json` (validated against
`foundry/schemas/client-manifest.schema.json`) and a generated
`release.lock.json`. Foundry (`foundry/`) auto-discovers every directory
under `clients/` and validates it — there is no separate registration step.

**Known limitation, stated plainly:** `client.manifest.json`'s `product` field
takes exactly one product id (see the schema — it is a single object, not a
list). In practice, `platform/application` is one Spring Boot binary that
compiles every `products/*` module unconditionally — `wego-divers`,
`wego-hr`, `wego-accounting`, `wego-payroll`, and `wego-travel-marketplace`
all ship together regardless of which single product a client declares. So
today, the manifest's `product.id` records which vertical the client is
commercially built around, not a real feature flag or tenant-scoping
boundary — every client currently gets the same backend. Foundry's own
README already says this directly: "a multi-product, multi-client validation
boundary, not a product generator." Don't imply otherwise when configuring a
new client.

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

4. **Generate the lock file.** From `foundry/`:

   ```bash
   pnpm install --frozen-lockfile
   pnpm run generate:lock
   ```

   This writes `clients/<client-id>/release.lock.json` — deterministic,
   hashed, no timestamps. Do not hand-write it.

5. **Validate.** Still from `foundry/`:

   ```bash
   pnpm run validate
   ```

   This resolves the new client against the module catalog and rejects it if
   the product id is wrong, a required module is missing, or the manifest has
   an unknown property. Fix and re-run `generate:lock` until this is clean.

6. **Confirm nothing else broke.** `bash scripts/repository-check.sh` from
   the repo root still needs to pass (it doesn't touch clients directly, but
   catches unrelated repository-invariant regressions). Run the full
   `contracts` CI job's steps locally if in doubt.

7. **If the client needs its own web app(s)**, follow the existing
   `web/apps/sharm-to-go-site`/`sharm-to-go-erp` or
   `web/apps/sharm-divers-club-site` as the real precedent for wiring a new
   Nuxt app into the `web/` pnpm workspace — there is no scaffolding command
   for this yet; it's still copy-and-adapt.

## What this process does NOT do

- **It does not deploy anything.** Onboarding a client here only makes it
  pass Foundry's own validation — see
  `docs/execution/WEGO_EXECUTION_BOARD.md` and the platform's own README for
  the current state of production deployment (there isn't one yet for any
  client).
- **It does not isolate data or infrastructure per client.** `deploymentIsolation:
  "ISOLATED_INSTANCE"` is a declared intent (see `docs/adr/0008-client-isolation.md`),
  not something this process provisions.
- **It does not scope which backend features that client's users can reach.**
  See the limitation above — every client currently gets the full backend.

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
