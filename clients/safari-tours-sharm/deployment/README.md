# Safari Tours Sharm — production deploy package (WEGO-016-OPS2-G)

> **Current VPS procedure, 2026-10-08:** actual HTTPS gateway is the Resort
> Docker container, NOT host Nginx. Use [CONTAINER_GATEWAY_RUNBOOK_AR.md](CONTAINER_GATEWAY_RUNBOOK_AR.md).
> Host-Nginx commands in the older runbook must NOT be executed on this VPS.
> Owner authorized the necessary gateway-container recreation only; keep both
> applications, databases, secrets, certificates and release lifecycles independent.

Prepared by Claude on 2026-10-07 for Codex, who reviews and later executes the
deploy after the owner's GO. **Nothing here has been run on a server.** No
secrets are stored in this directory; every real value is a `CHANGE_ME_…` or
`<…>` placeholder filled on the VPS only.

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
