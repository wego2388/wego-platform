# Backend Development

## Required runtime

The Gradle launcher, compiler, and application use JDK 25. Java 17 is not a supported launcher because the jOOQ 3.21 build plugin requires Java 21 or newer and Wego's approved baseline is Java 25.

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew :platform:application:check
./gradlew :platform:application:bootJar
```

The committed Gradle 9.5 wrapper verifies its downloaded distribution with SHA-256.

## Persistence workflow

Flyway migrations live in `platform/application/src/main/resources/db/migration`. The jOOQ code generator reads those migrations and writes generated Java types under the module's `build/` directory. Compilation depends on generation, so generated sources are neither committed nor hand-edited.

```bash
./gradlew :platform:application:jooqCodegen
./gradlew :platform:application:clean :platform:application:check
```

The integration gate starts real PostgreSQL 18.4 through Testcontainers, applies Flyway, inserts through the generated jOOQ model, and proves a database check constraint rejects an invalid event version. H2 is not used.

Every `@Testcontainers`-annotated test carries `disabledWithoutDocker = true`: if the
Docker daemon itself is genuinely unreachable, the test reports as skipped rather than
failing the build. This is a real gap the tests themselves cannot close — treat any CI
or local run with skipped integration tests as unverified, not passing, and confirm the
skip count is zero before trusting a green `check`.

`TESTCONTAINERS_RYUK_DISABLED=true` is set unconditionally for every `Test` task in
`platform/application/build.gradle.kts` — not scoped to any particular environment.
Ryuk (Testcontainers' resource-reaper sidecar) only exists to clean up containers a
crashed test run left behind; a CI runner is destroyed after the job regardless, and
locally a developer can `docker compose down`/`docker system prune` by hand, so the
image is simply never pulled and its own `docker.io` reachability is a non-issue here.
(An earlier version of this document had Ryuk pinned to a `ghcr.io` mirror-and-retag
fallback for the case where it was still enabled; that's moot now that it's disabled
unconditionally, and has been removed.)

The dependency that *is* still live is Testcontainers' own Postgres image string —
`postgres:18.4-alpine`, referenced directly (e.g. in `IdentityHttpTest`,
`LoginLockoutConcurrencyIntegrationTest`, `LoginRateLimitHttpTest`, and the other
`@Testcontainers` integration tests) so it resolves against whatever's already in the
local Docker image cache before ever reaching `docker.io`. Unlike the same image in
`infrastructure/compose/compose.yaml`, which is pinned to a digest on the AWS ECR
public mirror (`public.ecr.aws/docker/library/postgres:18.4-alpine@sha256:...`), the
bare `postgres:18.4-alpine` string Testcontainers uses has no such fallback — if
`docker.io` is unreachable on a given machine and the tag isn't already cached
locally, every `@Testcontainers` test in this module skips instead of running (see
`disabledWithoutDocker = true` above), which a green `check` won't surface as
anything other than a lower test count. If that happens, pull the same image through
the ECR mirror and retag it locally under the bare name Testcontainers expects, so the
next run resolves it from the local cache without touching `docker.io` at all:

```bash
docker pull public.ecr.aws/docker/library/postgres:18.4-alpine
docker tag public.ecr.aws/docker/library/postgres:18.4-alpine postgres:18.4-alpine
```

This is a one-time step per machine (the retagged image persists in the local Docker
image store) and is only needed if `docker.io` is actually unreachable — Docker
resolves an image from its local cache first, so once retagged, `docker.io` is never
consulted for this tag again until the local image is removed.

## Application configuration

The application reads these deployment-supplied values:

- `WEGO_DB_URL`
- `WEGO_DB_USERNAME`
- `WEGO_DB_PASSWORD`
- `WEGO_DB_POOL_SIZE`
- `WEGO_FLYWAY_ENABLED`

Flyway is disabled by default. Production must run an explicit migration step before application rollout. The development Compose profile may enable it for an isolated disposable database. No default user/password authentication is generated — the first account is always created explicitly through the `bootstrap-admin` profile below, never seeded automatically. Real email/password login, session issuance, and RBAC enforcement are delivered (WEGO-001); see `docs/architecture/SECURITY_MODEL.md` for what's still deferred to a later packet (OAuth, MFA, cookie transport, full RBAC administration).

## Test evidence

`check` includes Kotlin formatting/linting, compilation, pure domain tests, ArchUnit domain isolation, Spring Modulith verification, Spring Security/health tests, and the PostgreSQL migration/jOOQ integration test.

## First admin account

No default user or password ever exists. The first platform user is created by running the application with the `bootstrap-admin` Spring profile, which reads the email and password from an interactive console only (never a command-line argument, environment variable, or log) and exits immediately after — it never opens a network port. It refuses outright once any user already exists, so it can only ever create the first account, not a repeatable privilege-escalation path.

```bash
java -jar platform/application/build/libs/application-<version>.jar \
  --spring.profiles.active=bootstrap-admin
```

This requires a real interactive terminal (`System.console()` must be non-null); it will not run under a plain piped/non-TTY shell.

## Spring Modulith and generated jOOQ code

Every direct sub-package of the base package (`com.wego.*`) is a Spring Modulith module; only a module's root package is its public contract; nested packages (`domain`, `application`, `infrastructure`, `api`, and the same for a module's own name — e.g. `com.wego.generated.jooq.tables`) are internal to that module by Modulith's default convention, regardless of a `package-info.java`'s `displayName` (those files are not wired into either the Kotlin or Java source set today and are not compiled). Two consequences that apply to any future module:

- A type meant to be shared across modules (like `PermissionCode`) must live at the module's root package, not a sub-package — this is enforced by `ModuleArchitectureTest`, not just documented convention.
- jOOQ's generated `com.wego.generated.jooq.tables.*` classes are infrastructure every module's repository layer needs to reach directly. `platform/application/build.gradle.kts`'s `jooqCodegen` task writes a `package-info.java` marking `com.wego.generated` `ApplicationModule.Type.OPEN` after every generation (the directory is otherwise wiped and regenerated from scratch each time, so this can't be a hand-maintained file). Removing that hook reintroduces a Modulith boundary violation for every jOOQ-backed repository in the application, not just one module's.
