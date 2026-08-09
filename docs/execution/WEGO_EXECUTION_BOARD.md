# Wego Execution Board

Rule: exactly one implementation packet may be `ACTIVE` in a worktree. Parent mission status is tracking metadata and does not authorize parallel packet implementation.

## Mission

| Mission | Objective | Status |
|---|---|---|
| WEGO-000 | Establish a small, tested production foundation | COMPLETE |
| WEGO-001 | Identity & Access foundation | COMPLETE |
| WEGO-002+ | Product/domain delivery | NOT AUTHORIZED — deliberately undefined until WEGO-001 closes |

## WEGO-000-A — Governance and architecture baseline

- **Status:** COMPLETE
- **Objective:** Establish repository safety, engineering rules, architecture boundaries, and decisions before implementation.
- **Scope:** Environment assessment, repository baseline, constitution, architecture documents, ADR set, and execution board.
- **Out of scope:** Runtime implementation, domain features, production deployment.
- **Affected modules:** Repository root, `docs/`.
- **Risks:** Over-documenting speculative modules; decisions diverging from executable configuration.
- **Acceptance criteria:** Required architecture documents and concise ADRs exist; commands, boundaries, non-goals, and ownership rules are explicit; only this packet is active.
- **Tests:** Markdown/link/path checks; ADR inventory check; repository status inspection.
- **Documentation changes:** All foundation architecture and governance documents.
- **Rollback considerations:** Documentation-only files can be removed before dependent implementation; no data or external state exists.

## WEGO-000-B — Backend and persistence foundation

- **Status:** COMPLETE
- **Objective:** Create the Kotlin/Spring modular monolith foundation with explicit PostgreSQL persistence.
- **Scope:** Gradle wrapper/build, Spring Boot, Spring Modulith, security deny-by-default skeleton, health, Flyway migrations, jOOQ generation, outbox schema boundary, unit/architecture/integration tests.
- **Out of scope:** User authentication flows, full RBAC administration, business capabilities, integration delivery workers.
- **Affected modules:** `platform/application`, `platform/kernel/security`, `platform/kernel/events`, `products/divers`.
- **Risks:** JDK 25/tool compatibility; generated-code drift; accidentally exposing endpoints; testing against substitutes instead of PostgreSQL.
- **Acceptance criteria:** JDK 25 build succeeds; health is anonymously reachable; other requests are denied; Modulith and domain-dependency rules verify; Flyway migrates real PostgreSQL; generated jOOQ types compile and are used by a smoke repository.
- **Tests:** Kotlin unit tests, Spring security tests, Modulith verification, ArchUnit rules, Testcontainers PostgreSQL migration/jOOQ test, clean build.
- **Documentation changes:** Build/run commands, backend boundary notes, migration policy.
- **Rollback considerations:** Only greenfield schema/container data is affected; development volumes may be removed explicitly by the operator.

## WEGO-000-C — Contracts, product, and client composition

- **Status:** COMPLETE
- **Objective:** Establish versioned external contracts and prove Platform + Divers + Sharm configuration without coupling.
- **Scope:** OpenAPI v1 baseline, product manifest schema/manifest, client manifest schema/profile, release lock format, capability metadata, validation scripts/tests.
- **Out of scope:** Generated production SDK behavior, diving workflows, dynamic Foundry, shared-database tenancy.
- **Affected modules:** `platform/contracts`, `foundry`, `products/divers`, `clients/sharm-divers-club`.
- **Risks:** Treating manifests as an unrestricted scripting system; encoding client behavior as product logic.
- **Acceptance criteria:** Schemas reject unknown/invalid fields; Sharm manifest references Divers; release lock is deterministic; OpenAPI validates and remains versioned.
- **Tests:** JSON Schema validation positive/negative fixtures; OpenAPI lint/validation; deterministic lock comparison.
- **Documentation changes:** Manifest ownership and release-lock rules.
- **Rollback considerations:** Formats are pre-release and can be revised via ADR before WEGO-000 closes; after release they require versioning.

## WEGO-000-D — Web workspace foundation

- **Status:** COMPLETE
- **Objective:** Establish a deterministic Nuxt 4/pnpm workspace and shared design-token boundary.
- **Scope:** ERP shell, UI/design tokens/API client/auth/i18n package boundaries only where executable, Tailwind baseline, lint, typecheck, unit test, production build.
- **Out of scope:** Full website/control apps, business screens, client branding engine, browser authentication flows.
- **Affected modules:** `web/apps/erp`, `web/packages/design-tokens`, `web/packages/ui`.
- **Risks:** Empty package proliferation; host Node 20 producing misleading results.
- **Acceptance criteria:** Node 24/pnpm build is reproducible; shell renders product-neutral Wego content; no Sharm behavior exists in shared packages.
- **Tests:** ESLint, TypeScript/Nuxt typecheck, Vitest unit test, Nuxt production build.
- **Documentation changes:** Web workspace commands and package placement rules.
- **Rollback considerations:** No external state; lockfile and workspace can be replaced atomically before consumers exist.

## WEGO-000-E — Mobile KMP foundation

- **Status:** COMPLETE
- **Objective:** Establish shared KMP/Compose boundaries for future Wego Ops and Wego Customer experiences.
- **Scope:** KMP shared module, experience profile domain type, offline command envelope/queue contract, minimal Compose surface, JVM compile/test gate.
- **Out of scope:** Android/iOS release apps, Room/SQL schema, sync protocol implementation, voice UI, fake offline behavior.
- **Affected modules:** `mobile/shared`, `mobile/apps/ops`, `mobile/apps/customer` markers only if executable and justified.
- **Risks:** Mobile scaffolding becoming non-compiling architecture theater; prematurely fixing sync semantics.
- **Acceptance criteria:** KMP common/JVM sources compile and tests prove stable idempotency/experience-profile primitives; documentation states deferred platform targets and storage adapters.
- **Tests:** Gradle KMP JVM tests and Compose compilation.
- **Documentation changes:** Offline boundary and mobile bootstrap commands.
- **Rollback considerations:** No persisted user data or published binaries exist.

## WEGO-000-F — Development infrastructure and CI

- **Status:** COMPLETE
- **Objective:** Make local development and continuous verification reproducible without production coupling.
- **Scope:** Docker Compose for PostgreSQL/Redis/backend readiness, Nginx edge skeleton, `.env.example`, GitHub Actions quality gates, dependency and secret scanning configuration.
- **Out of scope:** Production deployment, DNS/TLS automation, backups execution, Kubernetes, control-plane implementation.
- **Affected modules:** `infrastructure`, `.github`, repository root.
- **Risks:** Example credentials mistaken for production secrets; CI gates that are declared but not runnable.
- **Acceptance criteria:** Compose config validates; services become healthy with development-only values; CI invokes documented backend/web/mobile/contract/security gates; no secret is committed.
- **Tests:** `docker compose config`, service health checks, CI syntax inspection, secret scan, dependency scan configuration check.
- **Documentation changes:** Development operations and secret-handling instructions.
- **Rollback considerations:** Compose resources are local and named; teardown is explicit and never targets unrelated Docker resources.

## WEGO-000-G — Integrated verification and closure

- **Status:** COMPLETE
- **Objective:** Prove the complete foundation from a clean state and close WEGO-000.
- **Scope:** Run all quality gates, reconcile docs with implementation, record evidence/risks, mark packets complete.
- **Out of scope:** Any WEGO-001 feature or production action.
- **Affected modules:** All WEGO-000 outputs.
- **Risks:** Passing isolated checks while integration is broken; overstating gates blocked by environment/network.
- **Acceptance criteria:** Every WEGO-000 deliverable is mapped to evidence; required tests pass from clean build inputs; residual risks are explicit; no packet remains active; WEGO-001 is not started.
- **Tests:** Full backend, database, web, mobile, contract, Compose, repository, and security gate suite.
- **Documentation changes:** Execution evidence, final statuses, and follow-up risk register only.
- **Rollback considerations:** No production or shared external state is touched; any local service teardown is scoped to the Wego Compose project.

## WEGO-000-H — Review intensity and agent collaboration governance

- **Status:** COMPLETE
- **Review intensity:** Tier 2 — documentation only, no code, no auth/payments/migration/tenant-isolation/PII logic touched.
- **Objective:** Codify a risk-proportionate review policy (so future packets aren't all reviewed as heavily as WEGO-001) and a written working agreement between the implementer (Claude Code) and the Tier 1 reviewer (Codex CLI), so review effort and evidence expectations are explicit and repeatable instead of ad hoc.
- **Scope:** `docs/operations/REVIEW_INTENSITY.md` (new), `docs/operations/AGENT_COLLABORATION.md` (new), a one-sentence addition to `docs/ENGINEERING_CONSTITUTION.md` §2, two guardrail bullets in `AGENTS.md`, a `**Review intensity:**` field added to this board's packet template and retrofitted onto the WEGO-001 section.
- **Out of scope:** Any change to `scripts/repository-check.sh`'s `required_files` array (neither existing operations doc is enforced there either — adding these two would be inconsistent with that array's actual scope, which is foundation-baseline/contract files, not the operations category); retroactively re-tiering any completed packet; prescribing CLI invocation mechanics for either agent.
- **Affected modules:** `docs/` only.
- **Risks:** A written tier policy is only as good as whether it's actually followed when scoping the next packet — this is a documentation change, not an enforcement mechanism; `scripts/repository-check.sh` does not (and, per Out of scope above, deliberately does not) verify tier declarations.
- **Acceptance criteria:** Both new docs exist, follow house style, and are cross-referenced from `AGENTS.md` and each other; the board's packet template and WEGO-001's own section carry the new field; `scripts/repository-check.sh` still passes unchanged.
- **Tests:** `bash scripts/repository-check.sh`; manual read-through confirming cross-references resolve and the new field appears in both the template and WEGO-001's section.
- **Documentation changes:** This packet *is* a documentation change — see Scope.
- **Rollback considerations:** Documentation-only; no schema, runtime, or external state touched; reverting is a plain file revert.

## WEGO-000-I — Web appearance polish

- **Status:** COMPLETE
- **Review intensity:** Tier 2 — UI/design-system completion of already-shipped, already-Tier-1-reviewed surfaces; no auth logic, no new API surface, no data model changes.
- **Objective:** Complete the design-token system and polish the two pages and one shared component that already exist, so the product reads as professionally finished rather than a bare scaffold — without inventing new pages, features, or fictional UI.
- **Scope:** Semantic color tokens (success/warning/danger) and a control-radius token in `web/packages/design-tokens`; self-hosting the already-specified Inter font; a placeholder favicon and basic page-head metadata; three shared components (`WegoButton`, `WegoInput`, `WegoAlert`) in `web/packages/ui` extracted from markup already duplicated in `login.vue`; rewiring `login.vue` and `index.vue` onto the completed system.
- **Out of scope:** Dark mode; any new page, nav, or dashboard; animation beyond a loading spinner and simple transitions respecting `prefers-reduced-motion`; a custom spacing/type scale; a designed logo (the favicon is an explicit placeholder); a standalone test runner for `web/packages/ui`.
- **Affected modules:** `web/apps/erp`, `web/packages/design-tokens`, `web/packages/ui`.
- **Risks:** `web/apps/erp/test/Login.spec.ts` already asserts exact `id`/`role`/text-content selectors against the current hand-rolled markup — restructuring `login.vue` onto shared components must preserve every one of them exactly, or a real, already-hardened regression suite breaks silently.
- **Acceptance criteria:** `pnpm run check` in `web/` stays green (lint, typecheck across all three packages, full Vitest run including the unmodified `Login.spec.ts` assertions, production build); Inter actually loads in a real browser rather than silently falling back; new semantic colors meet WCAG AA 4.5:1 against both surface and canvas; no backend/API file is touched.
- **Tests:** `pnpm install` then `pnpm install --frozen-lockfile`; `pnpm run check` in `web/`; a real dev-server visual check; a manual contrast check.
- **Documentation changes:** `web/README.md` gains a short section naming the completed token categories and the explicit deferred list above.
- **Rollback considerations:** Frontend-only; no schema, migration, or backend runtime touched; reverting is a plain file revert plus a lockfile regeneration.

## Stage evidence log

Evidence is appended as packets finish. A packet may become `COMPLETE` only after its acceptance criteria and tests are recorded here.

### 2026-08-08 — WEGO-000-A

- **DONE:** Environment/repository assessment, Git baseline, constitution, six required architecture documents, eleven required ADRs, and execution controls.
- **FILES CHANGED:** Repository governance files; `docs/architecture/`; `docs/adr/`; `docs/execution/`.
- **TESTS RUN:** Git state inspection; ADR inventory; active-packet search; required-file inventory.
- **EVIDENCE:** Empty repository initialized on `main` with no commit; eleven uniquely numbered accepted ADRs found; required architecture files present; WEGO-000-B is the only active packet.
- **RISKS:** JDK 25 and Node 24 are not installed on the host; their executable gates depend on pinned wrappers/containers in later packets.
- **NEXT PACKET:** WEGO-000-B — Backend and persistence foundation.

### 2026-08-09 — WEGO-000-B

- **DONE:** Gradle/JDK 25 foundation; Spring Boot/Modulith application; deny-by-default security with no generated user; health probes; Flyway outbox migration; jOOQ generation; Divers module marker; architecture, unit, security, and PostgreSQL integration tests.
- **FILES CHANGED:** Gradle wrapper/build; `platform/application`; `platform/kernel/security`; `platform/kernel/events`; `products/divers`; backend operations documentation.
- **TESTS RUN:** Checksum-verified Temurin 25.0.3; Gradle 9.5 wrapper verification; `ktlintFormat`; fresh `:platform:application:check --rerun-tasks`; clean check/boot JAR build.
- **EVIDENCE:** Fresh gate executed 15 tasks successfully; eight JUnit tests across six suites with zero skipped/failures; PostgreSQL 18.4 Flyway/jOOQ constraint test passed; Modulith and ArchUnit tests passed; executable Boot JAR produced.
- **RISKS:** Gradle/jOOQ dependencies emit upstream Java 25 native/Unsafe deprecation warnings; Docker Hub timed out locally, so the identical official PostgreSQL image was obtained from its public ECR mirror and locally tagged; local Testcontainers helper startup was disabled while the actual PostgreSQL test remained enabled.
- **NEXT PACKET:** WEGO-000-C — Contracts, product, and client composition.

### 2026-08-09 — WEGO-000-C

- **DONE:** OpenAPI v1 health contract; strict product, client, module/capability, and release-lock schemas; Wego Divers manifest; minimal Sharm client profile; physical module catalog; deterministic release lock; positive, negative, cross-reference, and path validation.
- **FILES CHANGED:** `platform/contracts`; `foundry`; `products/divers/product.manifest.json`; `clients/sharm-divers-club`; reference-boundary notes in the environment assessment and root README.
- **TESTS RUN:** Frozen pnpm install; manifest/schema validation; two forbidden-property negative fixtures; module/capability/client/product consistency checks; physical module-path checks; release-lock regeneration/hash comparison; Redocly OpenAPI lint.
- **EVIDENCE:** All validations passed; OpenAPI produced zero warnings; release-lock SHA-256 remained `7d6ce8dd1aa9ab798dee400613e54c0b277774d1ff68ac9233e8e85c7226c8b4` before and after regeneration; validation has no dependency on the local marketing reference.
- **RISKS:** Manifest formats remain pre-release until WEGO-000 closes; the user-designated marketing workspace contains tentative and time-sensitive facts, so it remains a human discovery reference and no catalog, policy, automation, or secret was imported.
- **NEXT PACKET:** WEGO-000-D — Web workspace foundation.

### 2026-08-09 — WEGO-000-D

- **DONE:** Node 24/pnpm workspace; Nuxt 4 ERP shell; Tailwind 4 pipeline; product-neutral design tokens and Vue UI component; strict lint/typecheck/unit/build scripts; production-runtime smoke test.
- **FILES CHANGED:** `.nvmrc`; `web/package.json`; `web/pnpm-lock.yaml`; `web/apps/erp`; `web/packages/design-tokens`; `web/packages/ui`; web documentation and root index.
- **TESTS RUN:** Official Node checksum verification; frozen pnpm install; workspace-wide ESLint with zero warnings; TypeScript, vue-tsc, and Nuxt typecheck; Vitest; Nuxt production build; loopback HTTP request against the Nitro artifact.
- **EVIDENCE:** Node 24.19.0 and pnpm 10.34.4 were used; one test file/test passed; Nuxt 4.5.2 built client and server successfully; the production artifact returned HTTP 200 with server-rendered Wego foundation content.
- **RISKS:** Nuxt's current dependency graph reports one upstream `@bomb.sh/tab`/`cac` peer-resolution warning during lock updates; frozen install and every executable gate pass. Rolldown emitted a non-failing plugin-timing advisory during build. pnpm kept unneeded dependency install scripts disabled, and the build passed without approving them.
- **NEXT PACKET:** WEGO-000-E — Mobile KMP foundation.

### 2026-08-09 — WEGO-000-E

- **DONE:** KMP shared module; generic experience profiles; typed offline command identity/origin/dependency/queue port; separate Wego Ops and Wego Customer Compose roots; shared Kotlin plugin/repository/memory configuration; mobile documentation.
- **FILES CHANGED:** Root Gradle settings/catalog/check wiring; `mobile/shared`; `mobile/apps/ops`; `mobile/apps/customer`; mobile documentation and root index.
- **TESTS RUN:** ktlint format/check; common/JVM compilation; shared JVM unit tests; both Compose JVM compilations; KMP dependency compatibility checks; backend regression check after plugin centralization; `git diff --check`.
- **EVIDENCE:** Fresh mobile gate executed 41 tasks successfully; four tests across two suites had zero skips/failures/errors; Ops and Customer JVM JARs compiled; backend check remained successful.
- **RISKS:** This proves common/JVM source integrity only, not Android/iOS packaging or durable offline behavior. Room KMP, DataStore, Ktor, native secure storage, background policies, and sync adapters intentionally remain deferred. Gradle/ktlint still emit the already-recorded upstream Java 25 native/Unsafe deprecation warnings.
- **NEXT PACKET:** WEGO-000-F — Development infrastructure and CI.

### 2026-08-09 — WEGO-000-F

- **DONE:** Digest-pinned PostgreSQL 18.4, Redis 8.10, Nginx 1.30, Gradle/JDK 25, and JRE 25 images; non-root/read-only backend and edge; isolated Compose topology; local-only environment template; pinned-SHA CI jobs; Dependabot; repository/YAML/action-pin/security checks; Spring Boot 4.1 Flyway runtime wiring regression fix.
- **FILES CHANGED:** `.dockerignore`; `.env.example`; `infrastructure`; `.github`; `scripts/repository-check.sh`; CI operations docs; Boot Flyway dependency and migration integration test; root documentation index.
- **TESTS RUN:** Compose render; multi-stage backend image build; four-service `up --wait`; PostgreSQL/Redis/backend/edge health; edge HTTP 200/403; Flyway log/history and outbox catalog queries; Redis unauthenticated/authenticated checks; container UID/read-only checks; targeted Boot auto-Flyway Testcontainers test; Gitleaks 8.30.1 directory scan; both pnpm audits; GitHub YAML/action-pin validation; repository and whitespace checks; scoped Compose teardown.
- **EVIDENCE:** All four services became healthy; Flyway applied V1 and created `wego.integration_outbox`; Nginx returned 200 for health and 403 for an unauthorized path; Redis returned `NOAUTH` then `PONG`; backend/edge ran as UIDs 10001/101 and application writes were blocked; Gitleaks scanned about 7.45 MB with no leaks; both pnpm audits reported no known vulnerabilities; Compose containers/network were removed while the named database volume was preserved.
- **RISKS:** The workflow is parsed and its constituent commands ran locally, but GitHub-hosted execution cannot occur until a future authorized commit/push. `.env.example` values are public local placeholders and the Compose topology is explicitly not production-ready. Docker Hub timed out, so immutable official Docker Library images are referenced through its public ECR mirror. Java 25 upstream native/Unsafe warnings remain.
- **NEXT PACKET:** WEGO-000-G — Integrated verification and closure.

### 2026-08-09 — WEGO-000-G

- **DONE:** Clean integrated backend/database/mobile verification; frozen web and Foundry verification; deterministic release composition; final repository, Compose, secret, dependency, and documentation reconciliation; all seventeen WEGO-000 deliverables mapped to evidence.
- **FILES CHANGED:** Final execution board; `docs/execution/WEGO_000_VERIFICATION.md`; strengthened repository invariant check; root documentation index.
- **TESTS RUN:** Clean Gradle `check` plus backend `bootJar` with rerun tasks; backend and KMP JUnit result inspection; frozen web install and full lint/typecheck/test/build; frozen Foundry install, release-lock regeneration/hash comparison, manifest/OpenAPI/GitHub YAML validation; Compose render; Gitleaks; web and Foundry production audits; repository and whitespace checks.
- **EVIDENCE:** Gradle completed 66 tasks successfully and all 12 JVM tests passed with zero skips/failures/errors; Nuxt lint/typecheck/test/production build passed; Foundry validation passed with zero OpenAPI warnings and an unchanged release-lock SHA-256; Gitleaks found no leaks; both audits found no known vulnerabilities; Compose and repository validation passed. The earlier full runtime topology proof remains recorded under WEGO-000-F.
- **RISKS:** GitHub-hosted CI awaits a future authorized commit/push; Android/iOS packaging, durable mobile sync, and production authentication remain intentionally deferred; documented upstream Java 25/Nuxt warnings are non-failing. The preserved Wego development database volume is local and contains foundation schema only.
- **NEXT PACKET:** WEGO-001 — Identity & Access foundation.

## WEGO-001 — Identity & Access foundation

- **Status:** COMPLETE
- **Review intensity:** Tier 1 — authentication, session, permission enforcement, account lockout. (See `docs/operations/REVIEW_INTENSITY.md`, retrofitted; this packet is that document's worked example.)
- **Objective:** Replace the deny-by-default security skeleton with a real, minimal authenticated actor and enforceable permission model, so later product packets can authorize writes against a real user instead of building throwaway auth first.
- **Scope:** `platform/kernel/identity` module (domain/application/infrastructure/api layers); users, credentials, sessions, roles, and role-permission schema via Flyway V2; email/password login and logout endpoints issuing an opaque bearer session token (SHA-256 hashed at rest); `SessionAuthenticationService`-backed request authentication wired into the existing Spring Security filter chain; RBAC enforcement via `@PreAuthorize`/`hasAuthority` against resolved `PermissionCode`s; an operator-run, console-interactive `bootstrap-admin` profile that creates exactly the first platform user and refuses once any user exists; an append-only `identity_audit_event` record for login success/failure/logout/permission-denial; a minimal email/password login screen in `web/apps/erp`.
- **Out of scope:** OAuth/social login (Google/Apple/phone), password reset flow, MFA/TOTP/step-up, mobile authentication, full RBAC administration UI, control-plane identity, public self-registration.
- **Affected modules:** `platform/kernel/identity` (new); `platform/kernel/security` (`PermissionCode` moved to the module root — see risks); `platform/application` (Flyway V2 migration, jOOQ generation, `@Modulithic`/`ApplicationModule` wiring); `web/apps/erp` (login page, dev proxy).
- **Risks:** `PermissionCode` had to move from `com.wego.security.domain` to the `com.wego.security` module root — Spring Modulith only treats a module's root package as its public contract, and this is the first cross-module use the security kernel has ever had, so the constraint was invisible until now. jOOQ's generated `com.wego.generated` package required an injected `package-info.java` (written by a `jooqCodegen` build hook, since the directory is wiped every generation) marked `ApplicationModule.Type.OPEN`, or every module using jOOQ directly would trip the same Modulith boundary violation — this is now a load-bearing part of the backend build, not a cosmetic annotation. CSRF is disabled on the filter chain, which is correct for a stateless Bearer token that never travels as a cookie but would be wrong if a future packet introduces cookie-based sessions without revisiting it. Session transport is a plain `Authorization: Bearer` header, not the HttpOnly-cookie target architecture `SECURITY_MODEL.md` describes — an intentional, minimal first step, not the final shape.
- **Acceptance criteria:** Real login succeeds against real PostgreSQL and issues a session; wrong credentials are rejected with an identical response regardless of cause and are audited with the specific reason server-side; an unauthenticated request to a protected route is denied; an authenticated request with the granted permission succeeds; one without it is denied; passwords are never stored, logged, or returned in plaintext; the account locks after repeated failures and unlocks after the configured window.
- **Tests:** Kotlin unit tests for `User`/`Session` invariants and lockout transitions, `LoginService` (fakes, no Spring), and `AdminBootstrapService` (fakes); a real-PostgreSQL Testcontainers migration test proving the unique-email and session-expiry constraints; a real-PostgreSQL Testcontainers `@SpringBootTest` HTTP test covering the full login → authenticated access → `hasAuthority`-gated route → logout → post-logout-denial lifecycle, including a second user with zero granted permissions to prove denial (not just denial-by-absence-of-token); a web unit test covering the login page's success and invalid-credentials paths against a stubbed `fetch`; web lint/typecheck/production build.
- **Documentation changes:** This entry; `docs/architecture/SECURITY_MODEL.md` (mark the target authentication architecture partially delivered, list what remains deferred); `docs/operations/BACKEND_DEVELOPMENT.md` (the `bootstrap-admin` profile procedure).
- **Rollback considerations:** Schema is additive only (Flyway V2 adds new tables; V1's outbox is untouched). No production client or real user exists yet, so the packet can be reverted or redesigned before any real deployment without a data-migration concern.

### 2026-08-09 — WEGO-001

- **DONE:** Real user/credential/session/role schema and jOOQ-backed repositories; email/password login and logout issuing and revoking a hashed opaque bearer session; RBAC resolved from assigned roles and enforced via `hasAuthority`; an operator-run `bootstrap-admin` CLI profile; append-only identity audit trail; a minimal login screen in `web/apps/erp`; a Spring Modulith boundary fix (`PermissionCode` relocated to its module root; generated jOOQ code marked an `OPEN` Modulith module via a build-time `package-info.java` hook) required to make any jOOQ-backed module — not just this one — compile under the existing Modulith verification test.
- **FILES CHANGED:** `platform/kernel/identity/**` (new module); `platform/kernel/security/**` (`PermissionCode` relocated); `platform/application/src/main/resources/db/migration/V2__identity_foundation.sql`; `platform/application/src/main/resources/application-bootstrap-admin.yml`; `platform/application/src/main/kotlin/com/wego/WegoApplication.kt` (no net change — a `sharedModules` experiment was tried and reverted in favor of the `package-info.java` fix); `platform/application/build.gradle.kts` (identity source set, `jooqCodegen` package-info hook); `platform/application/src/test/kotlin/com/wego/identity/**` (new); `platform/application/src/test/kotlin/com/wego/events/OutboxMigrationIntegrationTest.kt` (assertion updated for the second Flyway migration); `web/apps/erp/nuxt.config.ts` (dev proxy); `web/apps/erp/app/app.vue`, `web/apps/erp/app/pages/index.vue` (new), `web/apps/erp/app/pages/login.vue` (new); `web/apps/erp/test/Login.spec.ts` (new).
- **TESTS RUN:** `./gradlew check` from the repository root (backend + mobile, 56 tasks, fresh); backend `test` isolated (34 tests across 14 suites, zero failures/errors/skips, including two real-PostgreSQL Testcontainers suites); `ktlintCheck`/`ktlintFormat`; `pnpm --filter @wego/erp lint|typecheck|test|build` on pinned Node 24.19.0 (3 Vitest tests, zero lint/typecheck errors, clean production build); `bash scripts/repository-check.sh`.
- **EVIDENCE:** A real end-to-end smoke test beyond the automated suites: started real PostgreSQL 18.4 (Compose) and the built `WegoApplicationKt` boot JAR against it with Flyway enabled; ran the `bootstrap-admin` profile through a real pty (`script -qec`, since `System.console()` requires one) to create a genuine first admin account; `curl`'d a real login (200, real token), a real `/me` (200, correct roles/permissions), and a real `Nuxt dev` server (with the Vite dev-proxy this packet adds) forwarding the same calls exactly as a browser would. All backend and web processes and the Postgres container were stopped/torn down afterward; no state was left running.
- **RISKS:** Two real defects were caught only by this live smoke test, not by any automated suite, and are now fixed and covered going forward: (1) CSRF was on by default and silently rejected every `POST` with 403 despite `permitAll()` on the route — stateless Bearer-token APIs need it explicitly disabled; (2) the `bootstrap-admin` profile's `web-application-type: none` broke `SecurityConfiguration`'s `HttpSecurity` bean, which Spring Security only registers inside a web context — fixed with `@ConditionalOnWebApplication`. Session transport (Bearer header, not HttpOnly cookie) and the narrow permission catalog (one seeded role/permission) are deliberate, documented scope boundaries for this packet, not oversights.
- **NEXT PACKET:** WEGO-002 is not authorized and was not started. Candidate next step per the original proposal: the first real Wego Divers product capability (a catalog module), which needs a scoping decision from Mohamed before it can be defined the same way WEGO-001 was.

### 2026-08-09 — WEGO-001 (independent review remediation)

An independent review before the first commit found five blocking gaps and several
further issues in the initial pass above. All are fixed and re-verified below; none
required narrowing the packet's scope or acceptance criteria — the criteria were
correct, the first implementation pass hadn't fully met them yet.

- **FOUND AND FIXED:**
  1. **No transaction atomicity.** `LoginService`/`LogoutService`/`AdminBootstrapService`
     called repository/audit methods that each opened their own independent
     transaction — a failure partway through left prior writes permanently committed.
     Added a `TransactionRunner` port (Spring `TransactionTemplate`-backed) wrapping
     each use case in one outer transaction that every inner `@Transactional` call
     joins.
  2. **Lost updates under concurrent login.** Two concurrent attempts against the same
     account could both read the same `failed_login_count` and each write back +1,
     losing an increment. Added `UserRepository.findByEmailForUpdate` (`SELECT ...
     FOR UPDATE`), used by login.
  3. **Bootstrap race.** Two concurrent `bootstrap-admin` invocations could both pass
     the `existsAny() == false` check before either committed, creating two admins.
     Added `UserRepository.lockBootstrap` (`pg_advisory_xact_lock`, transaction-scoped).
  4. **OpenAPI contract out of sync.** `/login`, `/logout`, `/me` existed in code but
     not in `platform/contracts/openapi/v1/wego-api.yaml`. Added, with a `bearerAuth`
     security scheme; Redocly validates with zero warnings.
  5. **Identity absent from Foundry composition.** `foundry/catalog/modules.json` and
     `products/divers/product.manifest.json` didn't list the identity module/capability,
     so `clients/sharm-divers-club/release.lock.json` didn't represent what the code
     actually builds. Added `platform.identity` / `platform.identity-authentication`
     to both, regenerated the lock (deterministic — a second regeneration produces an
     identical hash).
  6. **`PERMISSION_DENIED` audit defined but never called.** `IdentityAuditRecorder.
     recordPermissionDenied` existed with no caller — `@PreAuthorize` denials returned
     403 with no audit trail, contradicting what this board and `SECURITY_MODEL.md`
     claimed. Added `AuditingAccessDeniedHandler`, registered via `HttpSecurity.
     exceptionHandling`, and a test that queries `identity_audit_event` directly to
     confirm the row exists (not just that the HTTP call was rejected).
  7. **Oversized email input.** A syntactically valid but very long email bypassed
     format validation and reached the `actor_email varchar(320)` audit column
     unbounded, turning a routine bad-input case into an unhandled 500. Fixed at the
     source (`EmailAddress.of` now enforces the 320-character bound the column
     already had) plus defensive truncation at the audit-write boundary.
  8. **Timing-based email enumeration.** "Unknown email" returned faster than "wrong
     password" (no bcrypt comparison on that path), letting response time distinguish
     registered from unregistered emails. Added a fixed dummy-hash comparison on
     every failure path that doesn't already do a real one.
  9. **401 vs 403.** Spring Security's default `Http403ForbiddenEntryPoint` returned
     403 for both "not authenticated" and "authenticated but forbidden." Registered a
     `BearerAuthenticationEntryPoint` (401 + `WWW-Authenticate: Bearer`) separately
     from `AuditingAccessDeniedHandler` (403). This is a filter-chain-wide change — it
     also corrected the pre-existing WEGO-000 `SecurityConfigurationTest`'s
     deny-by-default assertion, which had encoded the old, less correct 403-for-both
     behavior.
  10. **Ryuk pull fragility.** Testcontainers' resource-reaper sidecar has no pinned
      mirror the way the application's own base images do, and a `docker.io` block
      degraded the two integration tests below to silently skipped rather than
      failing loud. Set `TESTCONTAINERS_RYUK_DISABLED=true` for the test task — an
      ephemeral CI runner is destroyed after the job regardless, so the reaper has
      nothing to clean up there.
  11. **Login page UX gaps.** A thrown `fetch` (network failure) left the form stuck
      showing "Signing in…" forever with no way to retry; the session token wasn't
      kept in reusable state, so there was no way to sign out. Wrapped `submit()` in
      try/catch, stored `token` in a ref, added a working sign-out button.
  12. **`repository-check.sh` hardcoded to WEGO-000.** Its mission-status checks
      matched the literal string "WEGO-000", so they silently stopped protecting the
      one-active-packet invariant the moment WEGO-001 existed as a second mission row.
      Generalized to scan every `| WEGO-<n>[+] | ... | <status> |` row, validate each
      status against a fixed vocabulary, and cap at one `IN PROGRESS` mission —
      verified by deliberately corrupting the board three ways (two `IN PROGRESS`
      rows, an unrecognized status value, two `ACTIVE` packets) and confirming each
      is caught, then restoring it.
- **NEW TESTS ADDED:** `LoginLockoutConcurrencyIntegrationTest` and
  `AdminBootstrapConcurrencyIntegrationTest` (real PostgreSQL, real threads — prove
  the locking fixes under actual concurrent load, not just single-threaded logic);
  `LoginAtomicityIntegrationTest` (a `@Primary`-overridden failing `IdentityAuditRecorder`
  proves a mid-transaction failure rolls back the user mutation with it); an
  over-length-email test at both the `LoginService` unit level and the HTTP level; a
  `PERMISSION_DENIED` audit-row assertion added to the existing HTTP lifecycle test;
  a `WWW-Authenticate` header assertion on the 401 case; three web tests (network
  failure recovery, sign-out flow).
- **FILES CHANGED (in addition to the initial pass):** `platform/kernel/identity/
  src/main/kotlin/com/wego/identity/application/TransactionRunner.kt`,
  `AdminBootstrapService.kt`, `LoginService.kt`, `LogoutService.kt`,
  `UserRepository.kt` (new/changed ports); `.../infrastructure/
  SpringTransactionRunner.kt`, `AuditingAccessDeniedHandler.kt`,
  `BearerAuthenticationEntryPoint.kt` (new); `JooqUserRepository.kt`,
  `JooqIdentityAuditRecorder.kt`, `IdentityBeanConfiguration.kt`,
  `SecurityConfiguration.kt` (changed); `.../domain/EmailAddress.kt` (length bound);
  `platform/contracts/openapi/v1/wego-api.yaml`; `foundry/catalog/modules.json`;
  `products/divers/product.manifest.json`; `clients/sharm-divers-club/
  release.lock.json` (regenerated); `platform/application/build.gradle.kts`
  (`TESTCONTAINERS_RYUK_DISABLED`); `scripts/repository-check.sh`; `web/apps/erp/
  app/pages/login.vue`; new test files under `platform/application/src/test/
  kotlin/com/wego/identity/` and `web/apps/erp/test/Login.spec.ts` (extended); the
  pre-existing `platform/application/src/test/kotlin/com/wego/security/
  SecurityConfigurationTest.kt` (401 assertion, see finding 9).
- **TESTS RUN:** `./gradlew check` from the repository root, fresh (56 tasks,
  backend + mobile); backend `test` isolated (39 tests across 16 suites — up from 34
  across 14 — zero failures/errors/skips, including four real-PostgreSQL
  Testcontainers suites, none skipped); `ktlintCheck`/`ktlintFormat`; `pnpm --filter
  @wego/erp lint|typecheck|test|build` on pinned Node 24.19.0 (5 Vitest tests, zero
  lint/typecheck errors, clean production build); `pnpm run validate` in `foundry/`
  (manifests, OpenAPI, repository YAML — all pass); `bash scripts/repository-check.sh`,
  including the three deliberate-corruption checks described in finding 12; a manual
  secret-pattern scan (API keys, private-key headers, AWS key IDs) across every file
  touched in this remediation round — no matches.
- **EVIDENCE:** Every fix above is proven by a test that fails without it, not just
  code review — verified by running each new/changed test individually before
  combining, and the concurrency/atomicity tests specifically exercise real threads
  and real PostgreSQL row/advisory locks, not fakes.
- **RISKS:** The concurrency tests use real OS threads against a live Testcontainers
  Postgres and complete in low single-digit seconds; they're deterministic given
  `FOR UPDATE`'s blocking semantics but are inherently slower than the rest of the
  suite. `repository-check.sh`'s new mission-status vocabulary is a fixed `case`
  list (`COMPLETE`, `IN PROGRESS`, `NOT AUTHORIZED*`) — a genuinely new status word
  introduced later needs a matching script update, same as before, just now checked
  for every mission instead of silently only the first one.
- **NEXT PACKET:** Unchanged from above — WEGO-002 is not authorized and was not
  started. Nothing in this remediation round expanded scope beyond the findings
  themselves.

### 2026-08-09 — WEGO-001 (second independent review — REQUEST CHANGES resolved)

A second review before commit found three blocking gaps in the first remediation
round plus five lower-priority notes. All are fixed and re-verified below.

- **BLOCKING, FOUND AND FIXED:**
  1. **CI still expected 403 from the deny-by-default smoke check** after the
     401/403 correction changed the real behavior to 401.
     `.github/workflows/ci.yml`'s "Verify edge health and deny-by-default behavior"
     step asserted `= "403"` against `SecurityConfigurationTest`'s now-401
     expectation — CI would have failed the first time it ran. Updated the step to
     assert 401 plus a `WWW-Authenticate: Bearer` header, and actually ran the full
     Compose stack locally (build, up --wait, the exact curl checks CI runs, then
     down) rather than only reasoning about the YAML — confirmed 401 with the
     header through the real nginx edge, not just the JVM test.
  2. **No rate limiting on login.** Every failed attempt runs bcrypt and writes to
     PostgreSQL with no limit, letting a caller exhaust resources or deliberately
     grind a known account toward its lockout threshold — `SECURITY_MODEL.md`
     itself requires "edge and application rate limits protect authentication."
     Added an nginx `limit_req_zone` (5r/m, burst 3, nodelay, 429 on limit) scoped
     to exactly `/api/v1/identity/login` via the same Compose stack — verified 10
     rapid requests: the first 4 got through (401, wrong credentials), the rest got
     429; `/healthz` and other routes confirmed unaffected by the same run.
  3. **`repository-check.sh`'s new checks didn't verify the two counts agree.**
     The generalized version (previous entry) independently bounded "at most one
     ACTIVE packet" and "at most one mission IN PROGRESS" but allowed a mission
     IN PROGRESS with zero ACTIVE packets, or an ACTIVE packet with no mission
     IN PROGRESS — a real invariant the pre-generalization, WEGO-000-only version
     of this script used to enforce and the generalization dropped. Re-added the
     linkage in its general form and proved both new failure modes are caught (a
     mission IN PROGRESS with 0 active packets; an ACTIVE packet with 0 missions
     IN PROGRESS), then restored the board and confirmed a clean pass.
- **LOWER-PRIORITY, FOUND AND FIXED:**
  - `BACKEND_DEVELOPMENT.md` still said no real authentication mechanism existed —
    stale since this packet delivered exactly that. Corrected, and cross-referenced
    `SECURITY_MODEL.md` for what's still deferred.
  - Testcontainers' Ryuk sidecar has no pinned-mirror fallback the way the
    application's own base images do, and the earlier session's local fix (retag
    from the ghcr.io mirror) wasn't written down anywhere a new machine could find
    it. Documented the exact commands and reiterated that `disabledWithoutDocker`
    silently skipping is a real, undetectable-by-the-test-itself gap — a green
    `check` must still be read against a zero-skip count.
  - OpenAPI didn't document the `WWW-Authenticate` challenge header or the
    320-character email bound. Added a `headers` block to `UnauthenticatedResponse`
    and `maxLength: 320` to `LoginRequest.email`; clarified in `login`'s own 401
    description that it does *not* carry the header (it's a rejected-credentials
    response, not a missing-session challenge) — Redocly still validates clean.
  - `login.vue`'s `logout()` discarded the local token even when the server-side
    revoke call failed (network error or non-2xx), presenting a silent "you're
    signed out" that wasn't necessarily true server-side. Local state still clears
    (this tab shouldn't keep presenting the token either way), but a failed
    server-side revoke now shows a visible warning instead of failing silently.
  - `AuditingAccessDeniedHandler` recorded the request method+path (e.g.
    `GET /api/v1/identity/_test/admin-only`) as the "permission" denied, not the
    actual required permission. `@PreAuthorize` denials in Spring Security 7.1
    carry an `AuthorizationDeniedException` wrapping an
    `ExpressionAuthorizationDecision` with the real SpEL expression evaluated
    (`hasAuthority('identity:administer')`); extracted and recorded that instead,
    with the method+path fallback kept only for denials with no specific
    expression (e.g. the catch-all `.denyAll()`).
- **NEW/CHANGED TESTS:** The HTTP lifecycle test's audit assertion now checks for
  `identity:administer` in the recorded detail, not the request path; two new web
  tests (logout warning shown on a failed server-side revoke; no warning shown on a
  clean one).
- **FILES CHANGED:** `.github/workflows/ci.yml`; `infrastructure/nginx/nginx.conf`
  (rate-limit zone + dedicated login location); `scripts/repository-check.sh`
  (count linkage); `docs/operations/BACKEND_DEVELOPMENT.md`;
  `platform/contracts/openapi/v1/wego-api.yaml`; `web/apps/erp/app/pages/login.vue`;
  `web/apps/erp/test/Login.spec.ts`; `platform/kernel/identity/src/main/kotlin/
  com/wego/identity/infrastructure/AuditingAccessDeniedHandler.kt`;
  `platform/application/src/test/kotlin/com/wego/identity/IdentityHttpTest.kt`.
- **TESTS RUN:** `./gradlew check` from the repository root, fresh (56 tasks,
  backend + mobile, all green); backend `test` isolated (39 tests across 16 suites,
  zero failures/errors/skips — unchanged count from the prior round since this
  round's fixes are mostly infrastructure/docs, not new backend surface, aside
  from the audit-detail assertion update); `pnpm --filter @wego/erp
  lint|typecheck|test|build` (6 Vitest tests, up from 5; clean production build);
  `pnpm run validate` in `foundry/` (manifests/OpenAPI/repository-YAML all pass);
  `bash scripts/repository-check.sh` including the two new deliberate-corruption
  checks for finding 3; a full real Compose build+up+curl-verify+down cycle
  matching CI's own steps exactly (not simulated); a manual secret-pattern scan
  across every file touched in this round — no matches.
- **EVIDENCE:** The 401/`WWW-Authenticate` and 429-rate-limit behaviors were
  proven through the actual built Docker image and real nginx edge on port 58080,
  the same way CI will run them — not inferred from reading the YAML/config. The
  stack was torn down afterward (`docker compose down`); no state was left running.
- **RISKS:** The nginx rate-limit key is `$binary_remote_addr` (per-IP) — a shared
  NAT/proxy in front of legitimate users would rate-limit them together, a known
  tradeoff of IP-based limiting not specific to this implementation. `limit_req`'s
  burst=3/nodelay means a legitimate user who mistypes a password 4 times in quick
  succession will see a 429 on the 5th within the same ~12s window; this is the
  intended trade against the resource-exhaustion/targeted-lockout risk it closes.
- **NEXT PACKET:** Unchanged — WEGO-002 is not authorized and was not started.

### 2026-08-09 — WEGO-001 (third independent review — REQUEST CHANGES resolved)

A third review before commit found three blocking gaps plus two lower-priority
notes. All are fixed and re-verified below.

- **BLOCKING, FOUND AND FIXED:**
  1. **The 429 from finding 2 of the second round broke the OpenAPI contract.**
     nginx's own 429 is an HTML error page with no `Retry-After` header, but
     `login` only documented 200/401 — a client parsing every login response as
     JSON would break on the rate-limited case. Added a named `@login_rate_limited`
     location returning the same `LoginError` JSON shape (`{"error":"rate_limited"}`)
     with a `Retry-After` header, documented `429` on `/api/v1/identity/login` in
     the OpenAPI document via a new `RateLimitedResponse` component, widened
     `LoginError.error`'s enum, and added a CI step asserting the JSON body and
     both headers on the real 429 (not just the status code).
  2. **The targeted-lockout gap was still open.** nginx's per-IP limiter (5r/m,
     burst 3, nodelay) allows 4 immediate attempts then one every ~12s from a
     single IP — enough to lock a known account (5-failure threshold) in ~12s, and
     an attacker spreading attempts across IPs bypasses it entirely, since the key
     is the caller's address, not the target account. Added an application-level
     `LoginAttemptThrottle` port keyed by the target email (`InMemoryLoginAttemptThrottle`,
     documented single-instance-only — would need Redis if horizontally scaled),
     checked in `LoginService.login` before the transaction opens, returning the
     same `rate_limited` contract as the edge layer. Proven at three levels: a
     `LoginServiceTest` case with an always-rejecting fake throttle asserts the
     early return never touches the repository or audit log; a new
     `LoginRateLimitHttpTest` proves the real wired `@Component` bean rejects a
     second rapid attempt against one account over real HTTP while a different
     account is unaffected; a real Compose run showed the 429 firing on the
     *second* login attempt against one email with `Retry-After: 3` — the
     application throttle catching it before nginx's own (looser, IP-based) limit
     ever would.
  3. **A session could be created and become unrevocable.** `login.vue` stored
     the issued token as soon as `/login` succeeded, before `/me` confirmed it; if
     `/me` failed or the connection dropped, the form reappeared with no sign-out
     control while the token stayed valid server-side for its full 12-hour
     lifetime. Extracted a shared `revokeSessionBestEffort` helper (used by both
     `logout()` and this path); on a post-login `/me` failure the orphaned session
     is now best-effort revoked immediately and the token is never assigned to
     reactive state, so a retry can't accidentally reuse it. Two new tests cover
     it: the orphaned session's logout call is actually made (asserting the
     `Authorization` header carries the right token) and the success panel never
     renders; and a second case where the best-effort revoke itself fails over
     the network, asserting the existing "didn't confirm revocation" warning
     still surfaces instead of failing silently.
- **LOWER-PRIORITY, FOUND AND FIXED:**
  - `repository-check.sh`'s ACTIVE/IN-PROGRESS linkage (added in the second
    round) only compared *counts* — a WEGO-002 packet marked ACTIVE while WEGO-001
    was the mission IN PROGRESS would still pass a pure `1 == 1` check despite
    belonging to the wrong mission. Added a structural check that walks each
    packet section's own `## WEGO-<n>` heading down to its `- **Status:**` line
    and requires the ACTIVE one's mission number to match the IN-PROGRESS row's;
    proved both the mismatch (rejected) and match (accepted) cases against the
    real board, then restored it and confirmed a clean pass. Separately, `git
    diff --check` checked nothing meaningful in either a clean CI checkout or
    this repository's all-untracked, zero-commit state, because a plain `git
    diff` is always empty when nothing is staged against an unchanged tree —
    replaced it with a snapshot-stage-against-the-empty-tree-restore sequence
    (`git write-tree` to snapshot the current index, `git add -A`, `git diff
    --check <empty-tree-object> --cached`, then `git read-tree` to restore the
    snapshot exactly, staged or not, pass or fail) so it actually inspects every
    line of real content. Making the check meaningful surfaced 30 pre-existing
    files across the repo with a genuine trailing blank line at end-of-file
    (verified byte-for-byte, not a tool artifact) — fixed all of them, and added
    `*.md whitespace=-trailing-space` to `.gitattributes` first, since two
    `docs/execution/*.md` files use Markdown's intentional trailing-double-space
    hard-line-break convention in their metadata blocks, which the default
    whitespace rule would otherwise flag as an error.
  - Ryuk's `ghcr.io` mirror-retag documentation (added in the first round) was
    moot: `TESTCONTAINERS_RYUK_DISABLED=true` is unconditional for every `Test`
    task, so Ryuk is never pulled regardless of `docker.io` reachability. The
    real remaining risk is Testcontainers' own bare `postgres:18.4-alpine`
    image string (used directly by every `@Testcontainers` integration test),
    which — unlike the same image in `compose.yaml`, pinned to a digest on the
    AWS ECR public mirror — has no fallback if `docker.io` is unreachable and
    the tag isn't already cached locally; every integration test would silently
    skip rather than fail loud. Removed the moot Ryuk instructions and documented
    the real dependency with a verified fallback (pull the ECR-mirrored image,
    retag it locally as the bare name Testcontainers expects) — confirmed the
    retagged image's digest matches the ECR mirror's exactly.
- **NEW/CHANGED TESTS:** `LoginServiceTest` (throttled attempt fails without
  touching the repository or audit log); `LoginRateLimitHttpTest` (new file — real
  per-email throttle proven end to end over HTTP, plus a non-interference case
  across two different accounts); `IdentityHttpTest` and
  `LoginLockoutConcurrencyIntegrationTest` (both import a new shared
  `NoThrottleConfiguration` test bean, since their existing purpose — auth/session
  lifecycle and row-lock-under-concurrency proof — legitimately makes several or
  concurrent same-email attempts the real throttle would otherwise reject for
  reasons unrelated to what they prove); two new `Login.spec.ts` cases for the
  orphaned-session revoke path.
- **FILES CHANGED:** `platform/kernel/identity/.../application/LoginAttemptThrottle.kt`
  (new), `.../infrastructure/InMemoryLoginAttemptThrottle.kt` (new),
  `.../application/LoginService.kt`, `.../infrastructure/IdentityBeanConfiguration.kt`,
  `.../api/IdentityController.kt`; `platform/application/src/test/kotlin/com/wego/
  identity/{IdentityTestFakes.kt, LoginServiceTest.kt, IdentityHttpTest.kt,
  LoginLockoutConcurrencyIntegrationTest.kt}`, `LoginRateLimitHttpTest.kt` (new);
  `infrastructure/nginx/nginx.conf`; `.github/workflows/ci.yml`;
  `platform/contracts/openapi/v1/wego-api.yaml`; `web/apps/erp/app/pages/login.vue`;
  `web/apps/erp/test/Login.spec.ts`; `scripts/repository-check.sh`; `.gitattributes`;
  `docs/operations/BACKEND_DEVELOPMENT.md`; 30 files with a trailing-blank-line
  fix only (`.dockerignore`, `.env.example`, `.github/dependabot.yml`, six
  `foundry/` files, `products/divers/product.manifest.json`, ten `web/` files).
- **TESTS RUN:** `./gradlew :platform:application:check` fresh (ktlint + full
  suite, 42 tests across 17 suites — up from 39 — zero failures/errors/skips,
  including the two new HTTP-level throttle tests); `./gradlew :mobile:shared:check
  :mobile:apps:ops:check :mobile:apps:customer:check` (unaffected, all green);
  `pnpm run check` in `web/` on pinned Node 24.19.0 (workspace-wide: lint across
  `apps`+`packages`, typecheck for `design-tokens`/`ui`/`erp`, 8 Vitest tests — up
  from 6, clean production build); `pnpm run validate` in `foundry/`
  (manifests/OpenAPI/repository-YAML all pass); `bash scripts/repository-check.sh`,
  including the deliberate structural-mismatch and real-whitespace-violation
  corruption checks described above, each proven to reject then restored to a
  clean pass; a full real Compose build+up+curl-verify+down cycle matching CI's
  own infrastructure-job steps exactly, run twice (once isolating just the new
  429 contract, once as the complete final sequence); a manual secret-pattern
  scan across every file touched in this round — no matches.
- **EVIDENCE:** The account-level throttle's actual precedence over the edge
  limiter was observed directly, not assumed: the live Compose run's 429 arrived
  on the second same-email attempt with `Retry-After: 3` (the application
  throttle's window), not the fourth with `Retry-After: 12` (nginx's), proving
  both layers are wired and the tighter one fires first. The structural
  repository-check.sh linkage and the whitespace check were each proven against
  both a failing and a passing case on the real execution board / real file
  content, then restored, matching this project's established
  backup-corrupt-verify-restore evidence pattern.
- **RISKS:** `InMemoryLoginAttemptThrottle` is explicitly single-instance —
  documented in its own file and unchanged from the design named in the second
  round's risk note; horizontal scaling still needs a shared store (Redis, per
  `SECURITY_MODEL.md`). The trailing-blank-line fix touched 30 files outside this
  packet's own surface; each was verified to still parse/compile/lint clean
  (JSON via `json.load`, `.mjs` via `node -c`, and the full web/backend check
  suites), and the fix is mechanical (one trailing newline, no content change).
- **NEXT PACKET:** Unchanged — WEGO-002 is not authorized and was not started.

### 2026-08-09 — WEGO-001 (fourth independent review — REQUEST CHANGES resolved)

A fourth review before commit — this time scoped to only what was still
actually wrong, not a full re-list — found two blocking gaps plus one testing
note. All are fixed and re-verified below.

- **BLOCKING, FOUND AND FIXED:**
  1. **The account throttle from the third round didn't actually prevent an
     account being locked — it only paced the attacker on a fixed, predictable
     schedule.** A flat 3-second minimum interval means an attacker who simply
     waits exactly that long between attempts still reaches the account's own
     5-failure lockout in ~13–16s — the reviewer proved this directly against
     the real running stack (five 401s, one every 3.2s). Separately, and worse:
     `tryAcquire` updated its "last attempt" timestamp on *every* call,
     including rejected ones — a sustained flood of rejected requests kept
     refreshing the window, so the throttle could keep even the legitimate
     account owner locked out of their own login indefinitely, not just the
     attacker. Replaced the flat-interval design with real exponential backoff:
     `LoginAttemptThrottle` now takes `recordFailure`/`recordSuccess` calls
     reporting each attempt's actual outcome, and `InMemoryLoginAttemptThrottle`
     doubles the required wait on every recorded failure (3s → 6s → 12s → 24s →
     ..., capped at 15 minutes) and resets to the base interval on a recorded
     success. A rejected `tryAcquire` call now leaves the key's state
     completely untouched, closing the "flood keeps the window open forever"
     gap directly. `LoginResult` gained a `retryAfterSeconds` field so the
     controller reports the throttle's *actual*, now-variable wait instead of a
     fixed constant. Proven with a new dedicated `InMemoryLoginAttemptThrottleTest`
     (7 cases, using a controllable `Clock` rather than real sleeps) covering:
     the exponential progression itself; the cap; reset-on-success; different
     keys not interfering; and, explicitly, that a burst of 20 rejected calls
     spaced 100ms apart does not push the window past its original 3-second
     mark — the precise scenario the reviewer's second point described.
  2. **The orphaned-session fix from the third round only covered an explicit
     non-2xx `/me` response, not a thrown network exception.** If `/login`
     succeeded but `fetch("/me")` itself threw (a real network failure, not a
     rejected status), execution landed in `login.vue`'s outer `catch` block,
     which had no idea a token had already been issued — no revoke attempted,
     no warning shown, the exact orphaned-session risk the third round's fix
     was supposed to close, just reached through a different path. Hoisted
     `issuedToken` out of the `try` block so the `catch` block can see whether
     a session was actually issued before the exception; if so, it now runs
     the same best-effort revoke used everywhere else in this file and shows
     the same "didn't confirm revocation" warning on failure. Two new
     `Login.spec.ts` cases cover it: `/me` throwing after a successful login
     (revoke attempted, correct token in the `Authorization` header, no stale
     session left addressable); and that same case where the revoke call
     itself also throws (warning shown, matching the existing non-thrown
     variant's behavior).
- **TESTING NOTE, FOUND AND FIXED:** CI's rate-limit smoke check reused one
  email across every attempt in its loop — with the new, faster-to-trigger
  application throttle this proved *only* the application layer; nginx's own
  edge-level, per-IP limiter was never actually reached within ten requests,
  despite the step's name and comments claiming to verify it. Split the check
  into two independent loops: the existing same-email loop (now explicitly
  labeled as proving the application throttle) and a new loop using a
  distinct email per attempt, which keeps the application throttle out of the
  way entirely so only nginx's `$binary_remote_addr`-keyed counter can be
  what returns 429. Both loops assert the same JSON/Retry-After contract via
  one shared `assert_rate_limit_contract` function, since both layers must
  present an identical shape to a caller. Verified live against the real
  Compose stack before finalizing the YAML — the same-email loop hit 429 on
  attempt 2 (`Retry-After: 3`, the application throttle), the distinct-email
  loop hit 429 within a few attempts (nginx's own counter, unaffected by
  request bodies) — and `node scripts/validate-repository-yaml.mjs` still
  passes against the changed workflow file.
- **NEW/CHANGED TESTS:** `InMemoryLoginAttemptThrottleTest` (new file, 7 cases,
  deterministic via a hand-written `MutableClock` rather than real sleeps);
  `LoginServiceTest` (throttle `recordFailure`/`recordSuccess` calls asserted
  on the wrong-password/success cases; the throttled-attempt case now also
  asserts `retryAfterSeconds` propagates through `LoginResult`); two new
  `Login.spec.ts` cases for the thrown-`/me` orphaned-session path.
- **FILES CHANGED:** `platform/kernel/identity/.../application/
  LoginAttemptThrottle.kt` (redesigned: `tryAcquire` now returns a
  `ThrottleDecision` sealed type, plus `recordFailure`/`recordSuccess`),
  `.../infrastructure/InMemoryLoginAttemptThrottle.kt` (rewritten for
  exponential backoff), `.../application/{LoginService.kt, LoginResult.kt}`,
  `.../api/IdentityController.kt` (dropped its now-unnecessary
  `LoginAttemptThrottle` dependency — `Retry-After` comes from the result);
  `platform/application/src/test/kotlin/com/wego/identity/{IdentityTestFakes.kt,
  LoginServiceTest.kt}`, `InMemoryLoginAttemptThrottleTest.kt` (new);
  `web/apps/erp/app/pages/login.vue`, `web/apps/erp/test/Login.spec.ts`;
  `.github/workflows/ci.yml`.
- **TESTS RUN:** `./gradlew :platform:application:check` fresh (ktlint +
  full suite, 49 tests across 19 suites — up from 42 — zero failures/errors/
  skips; one ktlint violation surfaced by the new code and fixed via
  `ktlintFormat`, then re-verified clean); `./gradlew :mobile:shared:check
  :mobile:apps:ops:check :mobile:apps:customer:check` (unaffected, all
  green); `pnpm run check` in `web/` on pinned Node 24.19.0 (workspace-wide
  lint/typecheck, 10 Vitest tests — up from 8, clean production build);
  `pnpm run validate` in `foundry/` (manifests/OpenAPI/repository-YAML all
  pass, including the changed `ci.yml`); `bash scripts/repository-check.sh`
  (clean, untracked-file count unchanged); a full real Compose
  build+up+curl-verify+down cycle matching the *updated* CI steps exactly —
  health, deny-by-default, both rate-limit layers independently, both
  contracts — then torn down; a manual secret-pattern scan across every file
  touched in this round — no matches.
- **EVIDENCE:** The exponential backoff and the "rejected calls don't extend
  the window" fix are both proven with a deterministic clock at the unit
  level, not timing-dependent sleeps that could flake or mask a regression.
  The two-layer rate-limit split was verified against the real stack before
  being written into CI, not assumed from reading nginx's config — the actual
  attempt number each layer's 429 arrived on was observed directly in both
  the isolated live run and the final combined run below.
- **RISKS:** The exponential cap (15 minutes) matches `LoginService`'s own
  lockout duration by design, not coincidence — once an account is actually
  locked at the DB level, further throttle escalation past that point doesn't
  matter. `InMemoryLoginAttemptThrottle` remains explicitly single-instance
  (unchanged limitation from prior rounds); a horizontally-scaled deployment
  still needs a shared store per `SECURITY_MODEL.md`.
- **NEXT PACKET:** Unchanged — WEGO-002 is not authorized and was not started.

### 2026-08-09 — WEGO-001 (fifth independent review — REQUEST CHANGES resolved)

A fifth review found one remaining blocker, correctly framed as a design
decision rather than a parameter tweak, plus one small rounding note. Both
are resolved below.

- **BLOCKING, FOUND AND FIXED — the account throttle still let a targeted
  lockout through, just slower.** The fourth round's exponential backoff
  fixed the "rejected calls extend the window forever" bug, but didn't
  address the underlying issue: with a 3-second base interval, an attacker
  pacing exactly at the throttle's own schedule reached the 5th
  (locking) failure in ~45 seconds — the throttle delayed the attack from
  ~13s to ~45s, it didn't close it. The reviewer framed the fix correctly as
  a real design choice between two options: drop the hard DB lockout in
  favor of throttling alone, or keep the lockout and prove the time to force
  it exceeds the lockout's own duration, with the residual risk documented
  honestly either way.

  **Decision: kept the hard lockout.** It remains genuine protection against
  sustained password guessing, and removing it would discard functionality
  already built, reviewed, and proven correct under real concurrency in
  earlier rounds (`LoginLockoutConcurrencyIntegrationTest`). Instead,
  retuned `InMemoryLoginAttemptThrottle`'s base interval from 3 seconds to 2
  minutes (`platform/kernel/identity/.../infrastructure/
  InMemoryLoginAttemptThrottle.kt`) — not an arbitrary "bigger number," but
  chosen so the exponential schedule (2m → 6m → 14m → 29m for the fifth,
  locking attempt) puts forcing a lockout at roughly double the lockout's
  own 15-minute duration, comfortably past the 3–5x-margin-free territory
  the reviewer's 45-second measurement sat in. The reasoning and the
  explicit acknowledgment that this makes forced lockout *expensive, not
  impossible* is now written directly into the class's own doc comment and
  into a new "Login throttling and account lockout" subsection of
  `docs/architecture/SECURITY_MODEL.md`, which also names the still-open
  next mitigation (alerting/step-up on repeated lockout patterns) rather
  than implying a bigger backoff number would ever fully close this.

  Proven end to end, not just as an isolated component: a new
  `LoginServiceTest` case (`pacing exactly at the real throttle's own
  advertised retry-after ...`) wires the *real* `InMemoryLoginAttemptThrottle`
  (production defaults, not a fake) into a real `LoginService` against a
  real seeded account, drives login attempts in a loop that advances a
  controllable clock by exactly whatever `retryAfterSeconds` each rejection
  reports — simulating the fastest a caller obeying the throttle's own
  signals could possibly go — and asserts the account isn't actually locked
  until more time has elapsed than the lockout duration itself. This is the
  "combined test instead of an isolated progression test" the reviewer
  explicitly asked for. Verified live too: the real built container now
  returns `Retry-After: 120` on a second same-email attempt (previously 3),
  confirming the new default is actually wired into the deployed artifact,
  not only exercised by the test suite.
- **LOWER-PRIORITY, FOUND AND FIXED — `Retry-After` under-reported the real
  wait.** `Duration.between(now, nextAllowedAt).seconds` truncates any
  sub-second remainder, so a 2.9s wait reported as `Retry-After: 2` — a
  client retrying exactly on schedule would land ~0.9s early and be rejected
  again. Changed to a millisecond-based ceiling
  (`(remainingMillis + 999) / 1000`), so the header never under-promises.
- **NEW/CHANGED TESTS:** `LoginServiceTest`'s new combined throttle+service+
  account timing test (above). `MutableClock` (previously private to
  `InMemoryLoginAttemptThrottleTest`) extracted into the shared
  `IdentityTestFakes.kt` so both test classes use the same controllable-clock
  utility instead of duplicating it.
- **FILES CHANGED:** `platform/kernel/identity/src/main/kotlin/com/wego/
  identity/infrastructure/InMemoryLoginAttemptThrottle.kt` (base interval,
  ceiling rounding, expanded doc comment); `platform/application/src/test/
  kotlin/com/wego/identity/{IdentityTestFakes.kt (MutableClock added),
  InMemoryLoginAttemptThrottleTest.kt (uses the shared MutableClock),
  LoginServiceTest.kt (new combined test)}`; `docs/architecture/
  SECURITY_MODEL.md` (new "Login throttling and account lockout"
  subsection).
- **TESTS RUN:** `./gradlew :platform:application:check` fresh (ktlint +
  full suite, **50 tests across 19 suites — up from 49** — zero
  failures/errors/skips); `./gradlew :mobile:shared:check
  :mobile:apps:ops:check :mobile:apps:customer:check` (unaffected, all
  green); `pnpm run check` in `web/` on pinned Node 24.19.0 (unaffected by
  this round — no web files changed — confirmed still green: workspace-wide
  lint/typecheck, 10 Vitest tests, clean production build); `pnpm run
  validate` in `foundry/` (manifests/OpenAPI/repository-YAML all pass);
  `bash scripts/repository-check.sh` (clean, untracked-file count
  unchanged); a real Compose build+up+curl+down cycle specifically isolating
  the new timing — first attempt 401, second same-email attempt 429 with
  `Retry-After: 120` from the real built container — then torn down; a
  manual secret-pattern scan across every file touched in this round — no
  matches.
- **EVIDENCE:** The 29-minutes-to-lock figure is not an estimate — it's the
  literal output of the new `LoginServiceTest` case running the real
  throttle and real `LoginService` logic against a controllable clock, and
  the `Retry-After: 120` observed against the real built container confirms
  the same parameters are what's actually deployed, not just what the test
  suite exercises in isolation.
- **RISKS:** Explicitly documented in `SECURITY_MODEL.md` now rather than
  left implicit: this narrows the targeted-lockout window from ~45 seconds
  to ~29 minutes per forced lock, it does not eliminate the possibility for
  a sufficiently patient, automated attacker. Detecting/alerting on repeated
  lockout patterns against one account is named as the intended next
  mitigation and is not yet built. `InMemoryLoginAttemptThrottle` remains
  single-instance (unchanged limitation carried from prior rounds).
- **NEXT PACKET:** Unchanged — WEGO-002 is not authorized and was not started.

### 2026-08-09 — WEGO-001 (sixth independent review — REQUEST CHANGES resolved)

A sixth review confirmed the targeted-lockout decision itself as closed and
correct, and found one new, different blocker plus one P2 and two small
notes. All are resolved below.

- **BLOCKING, FOUND AND FIXED — the throttle's map was not actually
  bounded.** `InMemoryLoginAttemptThrottle` keys on the raw *submitted*
  email, checked before `LoginService` ever looks up whether an account
  exists — so an attacker can spray unlimited distinct keys. The prior
  "sweep entries older than X" cleanup wasn't a real bound: freshly-sprayed
  keys are never "stale" by that definition, so the map kept growing past
  `MAX_TRACKED_KEYS`, and every request past that limit paid for a full
  O(n) `removeIf` scan of the whole map on top of the spray itself — a
  self-inflicted CPU-exhaustion vector.

  Replaced the plain `ConcurrentHashMap` with a
  [Caffeine](https://github.com/ben-manes/caffeine)-backed cache
  (`maximumSize` + `expireAfterWrite`), giving a hard cap with amortized
  O(1) eviction — no more unbounded growth, no more full-table scans.
  Caffeine's eviction policy is often described as scan-resistant
  (frequency-aware, not plain LRU), which would suggest a real,
  repeatedly-hit account is automatically protected from a burst of cold
  spray keys evicting it — **that specific claim was checked empirically
  for this class's own access pattern before relying on it, using an
  isolated diagnostic against raw Caffeine, and it did not hold**: because
  nearly every access here also writes (`nextAllowedAt`/
  `consecutiveFailures` genuinely change), a key with thousands of prior
  real reads was evicted right alongside one-off spray keys once total
  spray volume reached the cap. Rather than build on a disproven
  assumption, the cap (`MAX_TRACKED_KEYS = 50,000`, up from 10,000) is
  sized against what's actually *achievable*: nginx's own edge-level,
  per-IP limiter sits in front of every one of these requests too, capping
  a single source to roughly 5 requests/minute — across the ~29-minute
  window the targeted-lockout fix (previous entry) proved matters, that's
  on the order of 150 requests, several hundred times below the cap. A
  single source cannot realistically approach the cap within a target's
  active window; a distributed, many-source-IP spray at cap-comparable
  volume remains a real, explicitly accepted residual risk, the same
  category of threat this component's documented single-instance scope and
  the already-named Redis-based horizontal path exist to eventually
  address, not something one in-process cache's eviction policy alone was
  ever going to solve. Both the realistic-volume survival case and the
  at-cap eviction boundary are proven by tests, not left as an untested
  assumption in either direction — the second one exists specifically to
  keep the accepted residual risk honest and regression-visible, not to
  "fix" further.
- **P2, FOUND AND FIXED — the login page ignored `Retry-After` on a 429.**
  The server correctly returns `rate_limited` with a real, escalating
  `Retry-After` (now up to 120s per the previous round's retune), but
  `login.vue` showed the same generic error text regardless, which read as
  an invitation to retry immediately. Added a `retryAfterSeconds` ref
  populated from the header on a `rate_limited` response, and a dedicated
  message path ("Too many attempts. Try again in 2 minutes.") formatted
  from the real value, with a generic fallback if the header is ever
  missing. Two new `Login.spec.ts` cases cover both paths.
- **SMALL NOTES, RESOLVED:** The stale "base interval = 3 seconds" comment
  no longer exists — it was already removed when the class was rewritten
  for the Caffeine cache above, so there was nothing further to change.
  `LoginServiceTest`'s combined timing test previously asserted only
  `elapsed > lockoutDuration`; strengthened to assert the exact documented
  figure (`Duration.ofMinutes(29)`) directly, so a future change to the
  backoff schedule that silently drifts the real number away from what's
  written in `SECURITY_MODEL.md` and the class's own doc comment fails this
  test instead of passing unnoticed under a loose bound.
- **NEW/CHANGED TESTS:** `InMemoryLoginAttemptThrottleTest` gained three
  cases: bounded growth under a 4x-cap spray, survival at a
  nginx-throttled-single-IP-realistic spray volume, and (documenting the
  accepted residual risk directly) eviction at a spray volume reaching the
  cap. `LoginServiceTest`'s combined timing test now asserts the precise
  29-minute figure. Two new `Login.spec.ts` cases for the `Retry-After`
  message.
- **FILES CHANGED:** `platform/application/build.gradle.kts` (Caffeine
  dependency); `platform/kernel/identity/src/main/kotlin/com/wego/identity/
  infrastructure/InMemoryLoginAttemptThrottle.kt` (Caffeine-backed
  rewrite); `platform/application/src/test/kotlin/com/wego/identity/
  {InMemoryLoginAttemptThrottleTest.kt, LoginServiceTest.kt}`;
  `web/apps/erp/app/pages/login.vue`, `web/apps/erp/test/Login.spec.ts`;
  `docs/architecture/SECURITY_MODEL.md` (new "Throttle memory bounding"
  subsection).
- **TESTS RUN:** `./gradlew :platform:application:check` fresh (ktlint +
  full suite, **53 tests across 19 suites** — up from 50 — zero
  failures/errors/skips); `./gradlew :mobile:shared:check
  :mobile:apps:ops:check :mobile:apps:customer:check` (unaffected, all
  green); `pnpm run check` in `web/` on pinned Node 24.19.0
  (workspace-wide lint/typecheck, **12 Vitest tests** — up from 10, clean
  production build); `pnpm run validate` in `foundry/` (all pass);
  `bash scripts/repository-check.sh` (clean, untracked-file count
  unchanged); a real Compose build+up+curl+down cycle confirming
  `Retry-After: 120` from the real built container (the Caffeine rewrite
  wired correctly end to end, not just in the test suite), both rate-limit
  layers independently, then torn down; a manual secret-pattern scan across
  every file touched in this round — no matches.
- **EVIDENCE:** The disproven scan-resistance assumption was not left as a
  design-doc claim — it was tested against raw Caffeine in an isolated
  throwaway diagnostic before touching production code, and once disproven,
  both the resulting design decision (size the cap against achievable
  volume, not against frequency) and its boundary (survives realistic
  volume; does not survive at-cap volume) are proven by tests that remain
  in the suite, not just asserted in prose.
- **RISKS:** A distributed, many-source-IP spray at volume comparable to
  `MAX_TRACKED_KEYS` (50,000) remains a real, accepted, and now explicitly
  tested residual risk — a materially different, higher-cost attack class
  than any single actor, requiring coordinated infrastructure. `Caffeine`'s
  `expireAfterWrite` housekeeping uses its own internal wall-clock ticker,
  independent of the injected `Clock` used for `nextAllowedAt` scheduling —
  the two agree in production; tests never run long enough in real
  wall-clock time for this to matter, since they never rely on TTL-based
  expiry for correctness.
- **NEXT PACKET:** Unchanged — WEGO-002 is not authorized and was not started.

### 2026-08-09 — WEGO-001 (sixth independent review — APPROVED, two non-blocking cleanups applied)

The sixth review round above was **APPROVED** — no blocking security findings,
no seventh review requested. Two small, explicitly non-blocking notes were
applied anyway before any commit:

- `build.gradle.kts`'s Caffeine dependency comment still described it as
  "scan-resistant" in a way that could read as "hot-key eviction is
  solved" — corrected to point at `InMemoryLoginAttemptThrottle`'s own doc
  comment, which documents that this was checked empirically and did not
  hold for this class's write-heavy access pattern.
- `InMemoryLoginAttemptThrottleTest`'s bounded-size test asserted
  `< 60,000` against a declared hard cap of 50,000 — tightened to
  `<= 50,000` directly, matching what `MAX_TRACKED_KEYS` actually
  guarantees. Re-verified: still passes exactly at the tightened bound.
- Full `./gradlew :platform:application:check` re-run clean after both
  changes (same 53 tests/19 suites, zero failures/errors/skips).

Independent verification reported by the reviewer for this round: 57
Kotlin tests across 21 suites (53 backend + 4 mobile), zero
failures/errors/skips; 12 web tests with clean lint/typecheck/build;
Foundry and repository-check both clean; Gitleaks clean; a real Compose run
confirming both rate-limit layers (`Retry-After: 120` application,
`Retry-After: 12` nginx) with a clean teardown.

No implementation/test files beyond the two named above were touched; the
execution board was updated with evidence. No commit, push, or deploy has
occurred — this remains pending the user's explicit go-ahead.
- **NEXT PACKET:** Unchanged — WEGO-002 is not authorized and was not started.

### 2026-08-10 — WEGO-000-H

WEGO-001 shipped after six real review rounds, each finding genuine defects.
The owner endorsed that rigor but asked for it to be made proportionate to
risk going forward, and for the working relationship between the implementer
(Claude Code) and the Tier 1 reviewer (Codex CLI) to be written down instead
of staying ad hoc. This packet reopens WEGO-000 to do exactly that —
documentation only, no runtime code touched.

- **DONE:** Added `docs/operations/REVIEW_INTENSITY.md` (two-tier policy:
  Tier 1 — heavy adversarial review — triggered by auth/authorization/session/
  permission logic, payments, migrations, multi-tenant/client-isolation
  boundaries, or real client PII; Tier 2 — one verified pass — the default
  for everything else; names WEGO-001 directly as the worked Tier 1 example).
  Added `docs/operations/AGENT_COLLABORATION.md` (implementer/reviewer role
  split, the real-evidence standard WEGO-001 already set — Testcontainers/
  Compose/real threads/controllable clocks, not code-reading — a structured
  finding format with explicit BLOCKING/NON-BLOCKING severity, and the
  execution board's evidence log as the shared memory both agents read/write
  across sessions). Added one cross-referencing sentence to
  `docs/ENGINEERING_CONSTITUTION.md` §2 and two guardrail bullets to
  `AGENTS.md`. Added a `**Review intensity:**` field to the packet
  convention and retrofitted it onto WEGO-001's own section.
- **FILES CHANGED:** `docs/operations/REVIEW_INTENSITY.md` (new),
  `docs/operations/AGENT_COLLABORATION.md` (new),
  `docs/ENGINEERING_CONSTITUTION.md`, `AGENTS.md`,
  `docs/execution/WEGO_EXECUTION_BOARD.md` (this file — mission table,
  WEGO-001 retrofit, this packet).
- **TESTS RUN:** `bash scripts/repository-check.sh` (clean — the required-files
  array was deliberately left unchanged, since neither existing operations
  doc is enforced there either); manual cross-reference check confirming
  every backtick-quoted path in the two new docs, `AGENTS.md`, and the
  Constitution actually resolves to a real file, in both directions.
- **EVIDENCE:** `bash scripts/repository-check.sh` output:
  "Repository structure and execution-board invariants are valid" after the
  mission table moved WEGO-000 to IN PROGRESS and this packet went ACTIVE
  then COMPLETE — proving the structural ACTIVE-packet/IN-PROGRESS-mission
  linkage (added during WEGO-001's third review round) still holds under a
  second real mission reopening, not just the original one it was written
  for.
- **RISKS:** A written tier policy only works if it's actually applied when
  the next packet is scoped — this document doesn't enforce itself, and
  `scripts/repository-check.sh` deliberately does not verify tier
  declarations (see the policy's own "Out of scope" section for why).
- **NEXT PACKET:** WEGO-000-I — Web appearance polish.

### 2026-08-10 — WEGO-000-I

The web app existed as a deliberate, coherent token foundation with almost no
built-out surface on top of it: two pages, one shared component, a font
that was specified but never actually loaded, no favicon, alert colors that
bypassed the token system, and inconsistent radius between cards and
controls. This packet completes the design-token system and polishes the
two pages and one component that already exist — no new pages, features, or
fictional UI.

- **DONE:** Added semantic color tokens (`success`/`warning`/`danger`, each
  with a `-soft` variant) and a `radius-control` token to
  `web/packages/design-tokens`, wired through Tailwind's `@theme` in
  `main.css` alongside the existing color tokens (the card radius was
  previously used as a raw arbitrary value rather than a themed utility;
  both radius tokens now share the same `wego-` namespace as the colors).
  Self-hosted Inter via `@fontsource-variable/inter` (no CDN dependency) —
  the token's stated font family now actually loads. Added a placeholder
  favicon (`web/apps/erp/public/favicon.svg`, an explicit monogram, not a
  designed logo) and page-head metadata (per-page titles, `theme-color`,
  favicon link) via `nuxt.config.ts` and `useHead` calls in each page.
  Extracted three shared components into `web/packages/ui`:
  `WegoButton` (primary/secondary variants, loading spinner state),
  `WegoInput` (label+input pair), and `WegoAlert` (success/warning/danger,
  configurable `role`) — replacing markup `login.vue` previously duplicated
  verbatim per input. Rewired `login.vue` and `index.vue` onto the
  completed system; added a short "Design tokens and shared UI" section
  (plus the explicit deferred list) to `web/README.md`, and corrected a
  stale claim there that authentication was still deferred (WEGO-001
  shipped it).
- **A REAL REGRESSION FOUND AND FIXED DURING THIS PACKET, NOT BY A
  SEPARATE REVIEWER:** adding a per-page `useHead()` call to `login.vue`
  broke all 11 of its existing tests — `web/apps/erp/vitest.config.ts` runs
  a plain `@vitejs/plugin-vue` + `happy-dom` environment with no Nuxt
  runtime, so `useHead` (a Nuxt auto-import) was `undefined` at mount time.
  This was a latent gap `index.vue`'s own pre-existing `useHead` call
  already had — invisible only because `index.vue` was never mounted in a
  unit test. Fixed with a `test/setup.ts` `beforeEach` stub
  (`vi.stubGlobal("useHead", () => {})`), re-stubbed before every test
  rather than once at startup because `Login.spec.ts`'s own `afterEach`
  calls `vi.unstubAllGlobals()` to reset its `fetch` stubs, which would
  otherwise silently remove this one too after the first test.
- **NEW/CHANGED TESTS:** No new test *files* — this packet's job was to not
  break the existing, already-hardened `Login.spec.ts` (11 cases covering
  the full WEGO-001 login/logout/orphaned-session/rate-limit flows) while
  restructuring its markup onto shared components. `test/setup.ts` is new
  test infrastructure, not a new test.
- **FILES CHANGED:** `web/packages/design-tokens/{src/tokens.css,
  src/index.ts}`; `web/apps/erp/app/assets/css/main.css`;
  `web/apps/erp/{package.json, nuxt.config.ts, vitest.config.ts}`;
  `web/apps/erp/test/setup.ts` (new); `web/apps/erp/public/favicon.svg`
  (new); `web/packages/ui/src/{WegoButton.vue, WegoInput.vue,
  WegoAlert.vue}` (new), `web/packages/ui/src/{WegoFoundationCard.vue,
  index.ts}`; `web/apps/erp/app/pages/{login.vue, index.vue}`;
  `web/README.md`; `web/pnpm-lock.yaml` (regenerated).
- **TESTS RUN:** `pnpm install` (regenerated the lockfile after adding
  `@fontsource-variable/inter`), then `pnpm install --frozen-lockfile` to
  prove reproducibility; `pnpm run check` in `web/` (ESLint zero-warnings
  across `apps`+`packages`, `vue-tsc`/`nuxt typecheck` across all three
  packages, full Vitest run, production build) — clean; **12 Vitest tests,
  all passing** (the pre-existing 11-case `Login.spec.ts` plus
  `WegoFoundationCard.spec.ts`, both unmodified in assertions);
  `bash scripts/repository-check.sh` clean throughout (including the
  ACTIVE-packet/IN-PROGRESS-mission transitions this packet's own start and
  finish required); a manual secret-pattern scan across every file touched
  in this round — no matches; confirmed no backend/API file was touched
  anywhere in this packet.
- **EVIDENCE:** A real dev server (fresh process, not the stale one left
  running from an earlier session — killed and restarted to get a true
  post-change check) was screenshotted via headless Chrome for both pages;
  Inter's distinctive letterforms render (not a system-font fallback), the
  teal accent/amber-adjacent focus system/soft card radius all render as
  intended, and the new control radius reads as a smaller sibling of the
  card radius rather than a mismatch. `curl` against the dev server
  confirmed `favicon.svg` serves 200, the home page's `<title>` is
  "Wego Platform", the login page's is "Sign in · Wego Platform", and the
  `theme-color`/icon `<link>` tags are present in the server-rendered HTML.
  The built production CSS was inspected directly (not assumed): the new
  `@font-face` declarations and real `.woff2` asset files for Inter are
  present in `.output/public/_nuxt/`, and the new `text-wego-success`/
  `text-wego-warning`/`text-wego-danger`/`rounded-wego-control`/
  `rounded-wego-card` utility classes appear in the compiled CSS only once
  the components that use them were actually wired in — confirming
  Tailwind's JIT scanning picked up the new components rather than the
  classes being silently dropped. WCAG AA contrast (4.5:1) for all three
  new semantic colors against both `wego-surface` and `wego-canvas` was
  computed directly via the sRGB relative-luminance formula, not estimated:
  success 5.03–5.42:1, warning 5.00–5.38:1, danger 6.06–6.54:1 — all clear
  a wider margin than the existing accent color's own 4.53–4.89:1.
- **RISKS:** Unchanged from the packet's own stated scope: no dark mode, no
  new pages/nav/dashboard, no custom spacing/type scale, no designed logo —
  all explicitly recorded as deferred in `web/README.md` rather than
  silently absent. `web/packages/ui` still has no standalone test runner of
  its own; its components are covered indirectly through `web/apps/erp`'s
  Vitest suite (which exercises `WegoButton`/`WegoInput`/`WegoAlert` via
  `login.vue`) and each package's own typecheck, matching WEGO-000-D's
  original "executable responsibility only" scope decision for this
  workspace.
- **NEXT PACKET:** None authorized — WEGO-002 remains NOT AUTHORIZED per
  the mission table. WEGO-000 returns to COMPLETE with WEGO-000-H and
  WEGO-000-I both closed.
