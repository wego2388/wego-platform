# Wego repository instructions

Read `docs/ENGINEERING_CONSTITUTION.md` and `docs/execution/WEGO_EXECUTION_BOARD.md` before implementation.

- Exactly one execution packet may be `ACTIVE` per implementation worktree.
- Do not start a later packet until the active packet's acceptance criteria and evidence are recorded.
- Preserve the Platform → Product → Client Configuration → Client Deployment boundary.
- Do not couple this repository to El Kheima Beach Resort OS or import secrets from any legacy project.
- Do not commit, push, deploy, or touch production unless explicitly authorized.
- Use Flyway exclusively for schema changes and jOOQ for primary SQL persistence.
- Keep domain code independent of Spring MVC, HTTP, jOOQ generated records, Redis, provider SDKs, and infrastructure implementations.
- Run the documented quality gates and report evidence; inspection alone is not proof.
