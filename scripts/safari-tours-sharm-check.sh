#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
expected_node="$(tr -d '[:space:]' < "$repository_root/.nvmrc")"
expected_pnpm="$(node -p "require('$repository_root/web/package.json').engines.pnpm" 2>/dev/null || true)"
jdk_home="${JAVA_HOME:-}"

if [[ -z "$jdk_home" && -x /home/wego/.jdks/temurin-25.0.3+9/bin/java ]]; then
  jdk_home="/home/wego/.jdks/temurin-25.0.3+9"
fi

if [[ -z "$jdk_home" || ! -x "$jdk_home/bin/java" ]]; then
  echo "JDK 25 is required. Set JAVA_HOME to a JDK 25 installation." >&2
  exit 1
fi

java_major="$($jdk_home/bin/java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
if [[ "$java_major" != "25" ]]; then
  echo "JDK 25 is required; found major version ${java_major:-unknown}." >&2
  exit 1
fi

node_version="$(node --version 2>/dev/null || true)"
if [[ "$node_version" != "v$expected_node" ]]; then
  echo "Node $expected_node is required; found ${node_version:-not installed}." >&2
  exit 1
fi

pnpm_version="$(pnpm --version 2>/dev/null || true)"
if [[ -z "$expected_pnpm" || "$pnpm_version" != "$expected_pnpm" ]]; then
  echo "pnpm ${expected_pnpm:-from web/package.json} is required; found ${pnpm_version:-not installed}." >&2
  exit 1
fi

export JAVA_HOME="$jdk_home"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$repository_root"
./gradlew :platform:application:test --rerun-tasks

cd "$repository_root/web"
pnpm install --frozen-lockfile
pnpm run check

cd "$repository_root/foundry"
pnpm run validate

cd "$repository_root"
node --check scripts/export-safari-tours-legacy-content.mjs
node -e '
  const { readFileSync } = require("node:fs");
  const { createHash } = require("node:crypto");
  const path = "clients/safari-tours-sharm/content-research/legacy-wordpress-export.json";
  const snapshot = JSON.parse(readFileSync(path, "utf8"));
  const { snapshotSha256, ...payload } = snapshot;
  const actual = createHash("sha256").update(JSON.stringify(payload)).digest("hex");
  if (!snapshotSha256 || snapshotSha256 !== actual) {
    throw new Error(`Legacy content snapshot digest mismatch: expected ${snapshotSha256}, got ${actual}`);
  }
  for (const record of [...snapshot.pages, ...snapshot.tours]) {
    if (record.contentApprovalStatus !== "UNVERIFIED_SOURCE") {
      throw new Error(`Legacy content ${record.id} is not marked UNVERIFIED_SOURCE`);
    }
  }
  for (const record of snapshot.media) {
    if (record.rightsStatus !== "UNVERIFIED" || record.migrationStatus !== "NOT_SELECTED") {
      throw new Error(`Legacy media ${record.id} is not safely quarantined`);
    }
  }
'
bash scripts/repository-check.sh
git diff --check

echo "Safari Tours Sharm quality gate passed."
