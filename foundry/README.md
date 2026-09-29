# Wego Foundry executable releases

Foundry is the repository-owned composition boundary for Wego client releases.
It discovers product and client manifests, resolves their module graph, and
generates two deterministic files for every client:

- `release.lock.json`: the exact platform/product module set.
- `release.plan.json`: the exact backend Gradle project, migration versions,
  site, staff app, Dockerfiles, API ownership, Compose bundle, and allowed data
  prefixes for that lock.

The trusted executable mappings live in
`catalog/release-profiles.json`. Client manifests remain small declarative
business inputs: they cannot contain secrets, SQL, hooks, shell commands, or
arbitrary class names.

From this directory:

```bash
pnpm install --frozen-lockfile
pnpm run generate:release
pnpm run generate:release
git diff --exit-code -- ../clients/*/release.lock.json ../clients/*/release.plan.json
pnpm run validate
```

Generation uses canonical JSON hashes and adds no timestamp or machine path.
Running it twice must be a no-op. Validation also proves that every declared
module is physically compiled by the selected backend, no undeclared product
source root is compiled, web package names and Compose services match, and all
referenced repository artifacts exist.

Foundry creates a release plan; it does not deploy it. A plan never grants
permission to push, deploy, change DNS, or use production credentials.
