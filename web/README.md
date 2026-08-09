# Wego web workspace

The foundation contains one product-neutral Nuxt application and only two
shared packages with executable responsibilities:

- `apps/erp`: authenticated-application shell boundary; authentication and
  business screens are intentionally deferred.
- `packages/design-tokens`: framework-neutral Wego visual tokens.
- `packages/ui`: reusable Vue presentation components; no client behavior.

Use Node 24.19.0 and pnpm 10.34.4:

```bash
corepack enable
pnpm install --frozen-lockfile
pnpm run check
```

Do not place authorization decisions in this workspace. UI visibility is a
usability concern; the API remains the enforcement boundary. Add another app or
package only when it owns an executable responsibility.

