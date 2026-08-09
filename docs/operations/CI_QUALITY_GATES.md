# CI quality gates

The foundation workflow runs independent repository, contract, backend, mobile,
web, secret, and dependency gates. All third-party actions are pinned to full
commit SHAs with the audited release noted in comments.

- Backend runs JDK 25 compilation, ktlint, unit/security/architecture tests, and
  the real PostgreSQL Testcontainers migration/jOOQ test.
- Mobile runs ktlint, shared tests, dependency compatibility checks, and both
  Compose JVM compilations.
- Contracts run strict manifest/lock checks and OpenAPI lint.
- Web runs frozen install, ESLint, three TypeScript gates, Vitest, and the Nuxt
  production build on Node 24.19.0.
- Infrastructure validates Compose, builds the non-root backend image, waits for
  all service health checks, proves edge health/deny behavior, and always tears
  down only the scoped CI project.
- Gitleaks checks repository history; pnpm audit checks both lockfiles;
  dependency review blocks new high-severity vulnerable dependencies on pull
  requests; Dependabot monitors Gradle, npm, Docker, and Actions inputs.
- Gradle dependency submission populates GitHub's dependency graph on `main`.

CI has read-only repository permission by default. Only the dependency graph job
receives `contents: write`, and only for pushes to `main`. CI does not deploy,
publish, mutate client data, or use production secrets.
