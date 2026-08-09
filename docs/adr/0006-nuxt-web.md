# ADR-0006: Nuxt web workspace

- Status: Accepted
- Date: 2026-08-08

## Context

Wego needs multiple web experiences with shared design, contract, authentication, and localization foundations.

## Decision

Use Nuxt 4, Vue 3, TypeScript, Tailwind CSS, and a pnpm workspace. Shared packages remain narrow and product-neutral; apps own product workflows.

## Consequences

SSR/static choices remain available per app. Node 24 LTS is the foundation runtime. Workspace lint, typecheck, tests, and production builds are CI gates.
