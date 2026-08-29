# Wego Foundry foundation

Foundry is currently a multi-product, multi-client validation boundary, not a
product generator. It discovers every direct child of `products/` and
`clients/`, requires the corresponding manifest (and a release lock for each
client), resolves each client to exactly its declared product/version, and
validates the physical module/capability graph. Manifests are declarative and
reject unknown properties. They must never carry secrets, SQL, executable
hooks, or arbitrary class names.

From this directory:

```bash
pnpm install --frozen-lockfile
pnpm run validate
pnpm run generate:lock
pnpm run generate:lock
git diff --exit-code -- ../clients/*/release.lock.json
```

`generate:lock` regenerates every discovered client lock, hashes canonical JSON
content, sorts resolved modules, and adds no timestamp or machine-specific
path. Running it twice must be a no-op. A manifest or catalog change must update
all affected locks in the same change. Schema evolution requires a new schema
version; released v1 files are not changed incompatibly.
