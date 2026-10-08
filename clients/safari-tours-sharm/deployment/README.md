# Safari Tours Sharm — production deploy package (WEGO-016-OPS2-G)

> **Live deployment, 2026-10-08:** Safari is running with independent HTTPS,
> a verified owner-admin login, encrypted off-site backup/restore and health
> timers. See [the current deployed handoff](../handoff/2026-10-08_PRODUCTION_DEPLOYED_HANDOFF_AR.md).
> OPS2-G remains ACTIVE for owner acceptance; do not repeat fresh-install steps.

> **Current VPS procedure, 2026-10-08:** actual HTTPS gateway is the Resort
> Docker container, NOT host Nginx. Use [CONTAINER_GATEWAY_RUNBOOK_AR.md](CONTAINER_GATEWAY_RUNBOOK_AR.md).
> Host-Nginx commands in the older runbook must NOT be executed on this VPS.
> Owner authorized the necessary gateway-container recreation only; keep both
> applications, databases, secrets, certificates and release lifecycles independent.

The original preparation below was authored by Claude on 2026-10-07 and
executed by Codex after explicit owner authorization on 2026-10-08. No secrets
are stored in this directory; examples retain placeholders. Actual source,
image IDs and acceptance evidence are in the deployed handoff, not the older
unfilled readiness template.

| File | Purpose |
|---|---|
| [RELEASE_READINESS.md](RELEASE_READINESS.md) | Manifest: SHA, images, migrations, permissions, gates table, open risks, owner `[!]` items |
| [DEPLOY_RUNBOOK_AR.md](DEPLOY_RUNBOOK_AR.md) | Ordered Arabic runbook with exact commands: preflight → baseline → images → env → loopback start → bootstrap admin → gateway/ACME/cert → external checks → cron → restore drill → rollback → STOP |
| [VERIFICATION_CHECKLIST.md](VERIFICATION_CHECKLIST.md) | Non-mutating smoke list (loopback + external), El Kheima before/after, evidence index |
| [.env.production.example](.env.production.example) | Every variable the stack and ops scripts read, with placeholders |
| [nginx/](nginx/) | Gateway vhost templates (Safari names only): full, ACME bootstrap, maintenance 503 + page, shared proxy snippet |
| `../../../infrastructure/compose/safari-tours-sharm.production.yaml` | Production Compose overlay (loopback edge only, no DB/app ports, prebuilt images, limits, log rotation, fixed project/volume names) |

Related: `docs/operations/SAFARI_TOURS_SHARM_OPERATIONS.md` (backup, restore,
upgrade, monitoring), `scripts/safari-ops/`, the Codex brief
`../handoff/2026-10-07_CLAUDE_PREPARE_CODEX_DEPLOY_VPS_HANDOFF_AR.md`.

Edge change shipped with this package: `infrastructure/nginx/nginx.conf` now
forwards `X-Forwarded-Proto: https` to the apps only when the TCP peer is a
loopback/private hop that sent exactly `https` (`$wego_forwarded_proto`);
otherwise it keeps its own `$scheme`. Real client IP for rate limits was
already taken from `X-Forwarded-For` of the same trusted hops; the gateway
template overwrites both headers instead of appending.
