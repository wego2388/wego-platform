# Dependency Audit Exemptions — Safari Tours Sharm
# Generated: 2026-10-05
# Audit command: pnpm --dir web audit --prod --audit-level high

## Exempted HIGH vulnerabilities (no fix available)

### 1. node-forge ≤1.4.0 — GHSA-86w9-cpqp-85rv
- **Advisory**: RSA PKCS#1 v1.5 signature verification accepts extra nested DigestAlgorithm elements
- **Latest available**: 1.4.0 (the vulnerable version IS the latest; `Patched versions: <0.0.0`)
- **Dependency path**: `@wego/safari-tours-sharm-erp > nuxt > @nuxt/cli > listhen > node-forge`
- **Risk assessment**:
  - `node-forge` is used by `listhen` (Nuxt's dev server listener) and `@nuxt/cli`, not by any
    application code path or the built production output.
  - The vulnerable RSA signature verification is only exercised if the application explicitly
    calls node-forge's signature API, which this project does not.
  - The package is present in the production `node_modules` of the ERP container image because
    `nuxt` is listed as a runtime dependency. However, the vulnerability requires active invocation
    of the affected API, not mere presence of the package.
  - No patched version exists upstream (confirmed 2026-10-05 against npm registry).
- **Mitigation**: Upgrade `nuxt` when a version that replaces or patches `listhen`/`node-forge`
  is released. Track nuxt changelog for node-forge removal.
- **Owner acknowledgement required before production deploy**: YES

### 2. braces ≤3.0.3 — GHSA-vfj7-8cjw-p6xm
- **Advisory**: Vulnerable to stack-exhaustion denial of service through deeply nested patterns
- **Latest available**: 3.0.3 (the vulnerable version IS the latest; `Patched versions: <0.0.0`)
- **Dependency path**: `@wego/safari-tours-sharm-erp > nuxt > @nuxt/nitro-server > nitropack > globby > fast-glob > micromatch > braces`
- **Risk assessment**:
  - `braces` is only called during file globbing at build/start time by Nitro internals.
    It is never called with user-controlled input at runtime.
  - A DoS via deeply nested patterns would require an attacker with write access to the
    file system glob patterns — effectively requiring code execution already.
  - No patched version exists upstream (confirmed 2026-10-05 against npm registry).
- **Mitigation**: Monitor `micromatch`/`braces` and `nitropack`/`globby` for patched releases.
  Upgrade via pnpm override once a patch is available.
- **Owner acknowledgement required before production deploy**: YES

## Gate status
These exemptions do NOT constitute deployment authorization.
Before production deployment, an explicit acknowledgement from the project owner (محمد)
is required for each exemption. Document the acknowledgement date and method in this file.

### Owner acknowledgements
- [ ] GHSA-86w9-cpqp-85rv (node-forge): acknowledged by _________ on _________
- [ ] GHSA-vfj7-8cjw-p6xm (braces): acknowledged by _________ on _________
