# Wiring the accessibility suite into CI — 2026-10-11

**Status:** added, every individual piece verified for real locally
before trusting it in the shared CI workflow — not written blind.

## What changed

- `.github/workflows/ci.yml`: new `sharm-to-go-accessibility` job,
  independent of the existing `infrastructure` job (which stands up a
  *different* product's Docker Compose stack entirely — the generic ERP
  on port 58080). Uses a GitHub Actions `services:` Postgres container
  (simpler than Compose for one plain dependency) plus the real
  `sharm-to-go` backend jar, the real catalog importer, and the real
  site production build — then runs
  `e2e/tests/sharm-to-go-accessibility.spec.ts` for real.
- `e2e/seed-sharm-to-go.mjs`: seeds one synthetic `platform-admin`
  directly in Postgres — the same deliberate, confirmed alternative to
  `AdminBootstrapRunner`'s real-TTY requirement that `e2e/seed.mjs`
  already uses for the other product (same `wego.identity_user`/
  `identity_user_role` schema, confirmed shared across products via
  `platform/kernel/identity`).
- `e2e/publish-sharm-to-go-samples.mjs`: publishes 5 sample services from
  the importer's own state file so the experience-detail/request pages
  have real data to test against, and prints the first published id for
  the CI step to capture into `WEGO_STG_E2E_SERVICE_ID`.
- `e2e/package.json`: two new scripts (`seed:sharm-to-go`,
  `test:sharm-to-go-accessibility`), no new dependencies.

## Why this needed real verification, not just writing correct-looking YAML

A broken shared CI workflow affects everyone's pipeline. Every piece was
tested for real, in order, against a fresh local Postgres + the real
backend jar + the real site build, before any of it went into the YAML:

1. `seed-sharm-to-go.mjs` — ran it, then confirmed the seeded account can
   actually log in via the real `/api/v1/identity/login` endpoint (not
   just that the INSERT succeeded).
2. `clients/sharm-to-go/scripts/import_catalog.py` — ran it against the
   seeded account's real token; 41/41 services created.
3. `publish-sharm-to-go-samples.mjs` — ran it against the same backend;
   5/5 published, confirmed the printed id is exactly the bare id (no
   extra output) a `$GITHUB_OUTPUT` capture needs.
4. The exact `pnpm --dir web --filter @wego/sharm-to-go-site run build`
   and `node web/apps/sharm-to-go-site/.output/server/index.mjs`
   invocations — run from the repo root (matching where a CI `run:` step
   actually executes, not a cd'd subdirectory) and confirmed working.
5. The full accessibility spec run against that real stack end to end:
   **33/33 passed.**
6. `actionlint` (downloaded and run locally, zero issues) plus this
   repo's own `foundry` `validate:repository-yaml` check (which
   specifically validates GitHub workflow YAML and action-pin
   immutability) — both clean.

**One real bug this caught before it reached CI:** `pip install requests`
fails outright on Ubuntu 24.04 (the same base image GitHub's
`ubuntu-24.04` runners use) — PEP 668's "externally managed environment"
guard rejects a bare system-wide `pip install` with exit code 1. Found by
actually running the exact command locally, not assumed. Fixed with
`--break-system-packages`, safe on a disposable, single-job CI runner
that's destroyed afterward.

## What this does NOT cover

A real GitHub Actions run of this exact job has not happened yet — that
can only be confirmed once this is pushed and CI actually triggers. Every
step was verified in isolation and in the same sequence CI will run them,
against equivalent tooling (same JDK, same Ubuntu 24.04 base, same pnpm/
node versions), which is the strongest confidence achievable without
triggering a real run from here.
