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

android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$android_home" && -d /home/wego/android-sdk ]]; then
  android_home="/home/wego/android-sdk"
fi
if [[ -z "$android_home" || ! -d "$android_home/platforms" ]]; then
  echo "An Android SDK is required (mobile/apps/sharm-to-go* modules, added in Packet 1D)." >&2
  echo "Set ANDROID_HOME/ANDROID_SDK_ROOT, or write sdk.dir=<path> to a local, gitignored local.properties." >&2
  exit 1
fi

export JAVA_HOME="$jdk_home"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$android_home"
export ANDROID_SDK_ROOT="$android_home"

cd "$repository_root"
./gradlew :platform:apps:sharm-to-go:check :platform:application:check
./gradlew :mobile:shared:check :mobile:apps:customer:check :mobile:apps:customer-android:check \
  :mobile:apps:sharm-to-go:check :mobile:apps:sharm-to-go-android:check

cd "$repository_root/web"
pnpm --filter @wego/sharm-to-go-site run lint
pnpm --filter @wego/sharm-to-go-site run typecheck
pnpm --filter @wego/sharm-to-go-site run test
pnpm --filter @wego/sharm-to-go-site run build
pnpm --filter @wego/sharm-to-go-erp run lint
pnpm --filter @wego/sharm-to-go-erp run typecheck
pnpm --filter @wego/sharm-to-go-erp run test
pnpm --filter @wego/sharm-to-go-erp run build

cd "$repository_root/foundry"
pnpm run validate

cd "$repository_root"
bash scripts/repository-check.sh
git diff --check

echo "Sharm To Go quality gate passed."
