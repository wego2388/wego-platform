# Wego Foundry foundation

Foundry is currently a validation boundary, not a product generator. It owns
versioned JSON Schemas, the catalog of modules that physically exist, and a
deterministic release lock. Manifests are declarative and reject unknown
properties. They must never carry secrets, SQL, executable hooks, or arbitrary
class names.

From this directory:

```bash
pnpm install --frozen-lockfile
pnpm run validate
pnpm run generate:lock
git diff --exit-code -- ../clients/sharm-divers-club/release.lock.json
```

`generate:lock` hashes canonical JSON content, sorts resolved modules, and adds
no timestamp or machine-specific path. A manifest or catalog change must update
the lock in the same change. Schema evolution requires a new schema version;
released v1 files are not changed incompatibly.

