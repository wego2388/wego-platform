#!/usr/bin/env bash
# Synthetic metadata only; no containers, production data or credentials.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
keys="$(printf 'assets/tour_media/test/asset-%08d.webp\n' {1..20000})"
archive_contains_key "$keys" 'assets/tour_media/test/asset-00000001.webp'
archive_contains_key "$keys" 'assets/tour_media/test/asset-00010000.webp'
archive_contains_key "$keys" 'assets/tour_media/test/asset-00020000.webp'
if archive_contains_key "$keys" 'assets/tour_media/test/asset-00020001.webp'; then
  die "absent key was accepted"
fi
if archive_contains_key "$keys" 'assets/tour_media/test/asset-00000001'; then
  die "prefix was accepted"
fi
if archive_contains_key "$keys" 'assets/tour_media/test/asset-00000001Xwebp'; then
  die "regex-like mismatch was accepted"
fi
if archive_contains_key '' 'anything'; then die "empty inventory was accepted"; fi
archive_contains_key '-literal[1].webp' '-literal[1].webp'
printf '%s\n' 'PASS: large archive inventory exact membership, missing/prefix/regex/empty rejection'
