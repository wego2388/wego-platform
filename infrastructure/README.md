# Wego development infrastructure

This Compose project is a local, single-client topology: PostgreSQL, ephemeral
Redis, the Spring Boot application, and an Nginx edge. Images and build bases
are immutable-digest pins from Docker Library's public ECR mirror. Ports bind to
loopback only. The backend and edge use read-only filesystems and non-root users.

Start it from the repository root:

```bash
docker compose --env-file .env.example \
  -f infrastructure/compose/compose.yaml \
  up --build --wait
curl --fail http://127.0.0.1:58080/healthz
```

Stop containers while preserving the development database:

```bash
docker compose --env-file .env.example \
  -f infrastructure/compose/compose.yaml \
  down
```

`down --volumes` deletes only the explicitly named Wego development database
volume, but is destructive and is never part of an automated command. Inspect
the project name and volume before running it.

`.env.example` contains public local-development placeholders. Copy it to an
ignored `.env` and replace values when needed. Production must use a distinct
secret set, restricted networking, TLS, backup policy, Redis security policy,
resource limits, and an approved release procedure; this file is not a
production deployment definition.


## Sharm To Go (single-VPS deployment)

Sharm To Go has its own backend, customer site and staff ERP, so it has its own
stack: `compose/sharm-to-go.compose.yaml`, `nginx/sharm-to-go.nginx.conf`,
`caddy/Caddyfile` and the `sharm-to-go-*.Dockerfile` files. See
[`SHARM_TO_GO_VPS.md`](SHARM_TO_GO_VPS.md) for the runbook.
