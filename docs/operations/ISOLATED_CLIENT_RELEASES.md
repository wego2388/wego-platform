# Isolated client release operations

This runbook is the common operational boundary for Safari Tours Sharm, Sharm
To Go and Sharm Divers Club. It is preparation and verification guidance; it
does not authorize a deployment.

## Non-negotiable boundary

One VPS/client environment owns one release plan, one Compose project name,
one environment file, one PostgreSQL database and volume, one backup location,
and one set of domains. Never point two client backends at the same database,
reuse a volume, copy session/payment secrets, or place two release bundles
under the same Compose project name.

The generated files under `clients/<client-id>/` identify the exact release:

- `release.lock.json` fixes the code-module graph.
- `release.plan.json` fixes the backend, migrations, site, staff app,
  Dockerfiles, Compose bundle and allowed data prefixes.

Before any release, run `pnpm --dir foundry run validate` and the backend/web
gates from CI. A stale plan is a hard stop.

## Secrets and configuration

Copy the applicable example to an ignored environment file on the target and
replace every placeholder. Restrict the file to the deployment account. Do not
commit it, paste it into tickets, or expose it in shell history/log output.

Required classes of secret are database credentials, session/bootstrap
credentials, and provider credentials actually used by that client. Safari
Paymob production mode additionally requires its verified merchant/integration
configuration and a completed sandbox checkout/callback/refund/reconciliation
gate. Absence of that evidence is a production NO-GO, not a reason to enable
the mock adapter; `TOURS_OPERATOR_PAYMOB_MOCK_ENABLED` must never be set in a
production environment.

## Preflight

1. Record the intended Git commit and verify a clean worktree.
2. Compare the client id, product and lock digest in `release.plan.json`.
3. Validate the exact Compose bundle with its intended environment file.
4. Confirm the public and staff domains, TLS termination and loopback/internal
   ports do not collide with another client.
5. Create and verify a fresh database backup before an upgrade.
6. Confirm free disk space for the image build, database growth and backup.

## Backup and restore boundary

Use `pg_dump` from the exact client's `postgres` service and store the encrypted
result outside the application volume with the commit, time, database name and
checksum. Periodically restore into a new disposable database and run health
plus client smoke tests; an untested dump is not backup evidence.

A restore is a maintenance operation: stop writes, confirm the exact target
project/database twice, preserve the failed database for investigation, restore
into a separate volume/database first, then verify Flyway history and business
record counts. Never restore one client's dump into another client's stack.

## Health and monitoring

Monitor edge `/healthz`, container health, restart count, disk usage, database
connections, backup age and certificate expiry. Alert on repeated login/payment
failures and migration errors without retaining credentials, query strings,
customer contact details, webhook bodies or payment tokens in logs.

## Upgrade and rollback

Build and test the exact release plan before stopping the existing release.
Run Compose with `--wait`, then verify public site, staff login boundary,
authenticated smoke paths and the absence of other products' routes.

Application/image rollback is allowed only while the database schema remains
compatible. Flyway migrations are forward-only: never delete history or blindly
run an older application against a migrated database. If a schema change makes
rollback incompatible, restore the pre-release backup into a separate database
and explicitly switch after validation. Record the incident and evidence.

## Client-specific entrypoints

- Safari Tours Sharm: `infrastructure/compose/safari-tours-sharm.compose.yaml`
- Sharm To Go: `infrastructure/compose/sharm-to-go.compose.yaml`
- Sharm Divers Club: `infrastructure/compose/sharm-divers-club.compose.yaml`

Each bundle is independently buildable. The Foundry validator rejects shared
backend projects, Compose files or web artifact paths across these releases.
