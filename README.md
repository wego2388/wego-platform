# Wego Platform

Wego is a product platform for independently deployed vertical business products.
The repository currently contains two separately composable product/client
pairs: Wego Divers + Sharm Divers Club, and Wego Travel Marketplace + Sharm To
Go. It does not contain or depend on El Kheima Beach Resort OS.

## Composition model

```text
Wego Platform + Wego Product + Client Configuration = Isolated Client Deployment
```

Each production client receives an isolated application deployment, PostgreSQL database, Redis configuration, secret set, storage namespace, and backup chain. This is not a shared-database multi-tenant system.

## Bootstrap prerequisites

- JDK 25
- Docker with Compose
- Node.js 24 LTS
- pnpm 10.34.4 through Corepack

Versioned wrappers and container build paths are provided so a globally installed Gradle is not required.

## Start here

- [Engineering constitution](docs/ENGINEERING_CONSTITUTION.md)
- [Architecture](docs/architecture/WEGO_ARCHITECTURE.md)
- [Boundary rules](docs/architecture/BOUNDARIES.md)
- [Execution board](docs/execution/WEGO_EXECUTION_BOARD.md)
- [WEGO-000 verification record](docs/execution/WEGO_000_VERIFICATION.md)
- [Environment assessment](docs/execution/ENVIRONMENT_ASSESSMENT.md)
- [Backend development](docs/operations/BACKEND_DEVELOPMENT.md)
- [Foundry manifest validation](foundry/README.md)
- [Sharm Divers Club client](clients/sharm-divers-club/README.md)
- [Sharm To Go client and execution plan](clients/sharm-to-go/README.md)
- [Web workspace](web/README.md)
- [Mobile workspace](mobile/README.md)
- [Development infrastructure](infrastructure/README.md)
- [CI quality gates](docs/operations/CI_QUALITY_GATES.md)

Implementation commands are added and verified by the corresponding WEGO-000 packets. No production deployment command is part of WEGO-000.
