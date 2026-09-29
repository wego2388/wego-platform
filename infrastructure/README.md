# Wego isolated client infrastructure

Every client release has an explicit Compose bundle and its own PostgreSQL
volume. Images and build bases use immutable digest pins; published ports bind
to loopback by default; application containers are non-root and read-only.

| Client | Compose bundle | Backend |
|---|---|---|
| Safari Tours Sharm | `compose/safari-tours-sharm.compose.yaml` | `:platform:apps:safari-tours-sharm` |
| Sharm To Go | `compose/sharm-to-go.compose.yaml` | `:platform:apps:sharm-to-go` |
| Sharm Divers Club | `compose/sharm-divers-club.compose.yaml` | `:platform:application` |

`compose/compose.yaml` is only a backward-compatible include for the explicit
Safari local bundle. New commands and automation must name the client file.

Example local start:

```bash
docker compose --env-file .env.example \
  -f infrastructure/compose/safari-tours-sharm.compose.yaml \
  up --build --wait
curl --fail http://127.0.0.1:58080/healthz
```

Stop while preserving the database:

```bash
docker compose --env-file .env.example \
  -f infrastructure/compose/safari-tours-sharm.compose.yaml down
```

Never add `--volumes` unless the exact disposable project and named volume have
been inspected: that option deletes its database. `.env.example` contains only
local placeholders. Production secrets, TLS/DNS, Paymob credentials, backup
retention and monitoring must be supplied through the approved operator
process; none are stored in a release plan.

See [the isolated-client runbook](../docs/operations/ISOLATED_CLIENT_RELEASES.md)
and [the Sharm To Go VPS notes](SHARM_TO_GO_VPS.md).
