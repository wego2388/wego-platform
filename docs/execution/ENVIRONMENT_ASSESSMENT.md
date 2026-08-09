# WEGO-000 Environment and Architecture Assessment

Date: 2026-08-08  
Status: complete  
Target: `/home/wego/wego-platform`

## Safety assessment

- `/home/wego` is a user home directory, not a Git repository.
- `/home/wego/wego-platform` did not exist before WEGO-000.
- No existing worktree, branch, tracked file, or untracked project file overlaps the target.
- Existing Wego and El Kheima-related directories are outside the target. They are not dependencies and will not be modified or inspected for secrets.
- At the user's direction on 2026-08-09, `/home/wego/projects/sharm-divers-club-marketing` is a read-only client/domain discovery reference. Only its non-secret high-level instructions, approved decisions, and catalog rules were inspected; it is not a build/runtime dependency or an authority over Wego architecture.
- WEGO-000 will create a new Git repository but will not commit, push, deploy, modify DNS, or touch production.

## Environment assessment

| Capability | Observed | Consequence |
|---|---|---|
| OS workspace | Linux, writable `/home/wego` | Supported local development host |
| JDK | JDK 17 exists at `/home/wego/jdk17`; Java is not on `PATH` | JDK 25 must be supplied reproducibly; no fallback to 17 |
| Gradle | Not on `PATH`; Gradle 8.12 distribution cached | Commit Gradle 9.5 wrapper; 8.12 is not used because Gradle requires 9.1+ to run on Java 25 |
| Node.js | Host default 20.20.0; checksum-verified user-local 24.19.0 added during bootstrap | Nuxt gates use the pinned Node 24 runtime; host Node 20 is never accepted as build evidence |
| pnpm | 10.34.4 | Suitable package manager; lockfile required |
| Docker | 29.2.1 | Suitable for development services and reproducible build verification |
| Docker Compose | 5.0.2 | Suitable for the single-client development topology |
| Target Git state | No repository existed | Safe greenfield initialization |

## Foundation decisions

- Modular monolith with one deployable Spring Boot application.
- Logical business modules are Spring Modulith modules; physical placement follows `platform/`, `products/`, and `clients/` boundaries.
- JDK 25 is the compilation and runtime baseline.
- Gradle 9.5 is pinned because it runs on Java 25 and is inside Kotlin Multiplatform 2.4.10's supported Gradle range.
- Spring Boot 4.1.0 and Spring Modulith 2.1.0 are pinned as compatible current stable lines.
- Spring Boot dependency management owns the jOOQ, Flyway, PostgreSQL JDBC, and Testcontainers versions unless an ADR explicitly overrides them.
- jOOQ generated sources derive from Flyway migrations; generated code is build output, not hand-edited source.
- PostgreSQL is tested with a real container. No H2 compatibility substitute is introduced.
- Nuxt uses Node 24 LTS in CI and containers. The host's Node 20 is not accepted as evidence for the web build.
- Mobile is a deliberately narrow KMP/Compose foundation: shared architecture primitives and compilable JVM validation, not a pretend production app or premature Android/iOS packaging setup.
- Client manifests prove product composition without adding `tenant_id` to operational tables.

## Primary risks and controls

| Risk | Control |
|---|---|
| JDK 25 is absent locally | Build and test with pinned JDK 25 container/toolchain; document bootstrap |
| Host-default Node is below Nuxt minimum | Pin `.nvmrc` to 24.19.0 and use Node 24 for local/CI/container verification |
| Dependency download availability | Pin versions and lock package dependencies; report any network-limited gate separately |
| jOOQ generation drifts from migrations | Wire compilation to code generation and add a clean-tree consistency CI check |
| Empty module theater | Create only security, event/outbox, product marker, client manifest, and application composition responsibilities required by WEGO-000 |
| Security skeleton mistaken for complete authentication | Deny by default, permit health only, and document authentication/session delivery as a later packet |
| Control-plane privilege leakage | Keep control-plane design metadata-only and outside client business authentication |
| Product/client coupling | Validate product and client manifests and keep Sharm-specific data under `clients/sharm-divers-club/` |

## Bootstrap success conditions

WEGO-000 is complete only after repository checks, Kotlin compilation/tests, Modulith verification, a real PostgreSQL migration test, jOOQ generation, OpenAPI validation, web lint/typecheck/test/build, mobile compilation/tests, Compose service health checks, and secret/dependency scanning configuration have objective evidence. Any unavailable external scanner is reported as a residual verification risk rather than silently treated as passed.
