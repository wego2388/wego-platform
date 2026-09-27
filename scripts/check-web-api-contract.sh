#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_directory="$(mktemp -d)"
trap 'rm -rf "$temporary_directory"' EXIT

cd "$repository_root/web"
pnpm exec openapi-typescript \
  ../platform/contracts/openapi/v1/wego-api.yaml \
  -o "$temporary_directory/generated.ts" >/dev/null

generated_contract="packages/api-contract/src/generated.ts"
if ! cmp -s "$temporary_directory/generated.ts" "$generated_contract"; then
  echo "Generated web API contract is stale. Run: cd web && pnpm run generate:api-contract" >&2
  diff -u "$generated_contract" "$temporary_directory/generated.ts" || true
  exit 1
fi

echo "Generated web API contract matches OpenAPI."
