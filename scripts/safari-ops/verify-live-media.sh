#!/usr/bin/env bash
# Verifies the LIVE media volume against the live database after a restore
# (runbook section 4, step 8): every tours_operator_asset original exists
# with the recorded size and SHA-256, every variant exists with its recorded
# size, and files/directories keep the private owner and modes
# (10001:10001, dirs 0700, files 0600). Read-only: the volume is mounted :ro.
#
#   SAFARI_COMPOSE_PROJECT=wego-safari-tours-sharm scripts/safari-ops/verify-live-media.sh

source "$(dirname "$0")/lib.sh"

container="$(postgres_container)"
media_volume="$(docker volume ls -q \
  --filter "label=com.docker.compose.project=${SAFARI_COMPOSE_PROJECT}" \
  --filter "label=com.docker.compose.volume=media" | head -1)"
[ -n "$media_volume" ] || media_volume="${SAFARI_COMPOSE_PROJECT}-media"
[ -n "$(docker volume ls -q --filter "name=^${media_volume}$")" ] || die "media volume $media_volume not found"

expected="$(container_psql "$container" -c \
  "SELECT 'asset' || chr(9) || storage_key || chr(9) || sha256 || chr(9) || file_size_bytes FROM wego.tours_operator_asset
   UNION ALL
   SELECT 'variant' || chr(9) || storage_key || chr(9) || '-' || chr(9) || file_size_bytes FROM wego.tours_operator_asset_variant")" \
  || die "cannot read asset tables"

actual="$(docker run --rm --network none -v "${media_volume}:/m:ro" \
  public.ecr.aws/docker/library/alpine:3.20 sh -c '
    cd /m || exit 2
    bad="$(find . -type d ! -perm 700 | head -5; find . -type f ! -perm 600 | head -5; find . \( ! -user 10001 -o ! -group 10001 \) | head -5)"
    [ -z "$bad" ] || { echo "PERM $bad"; }
    find . -type f -exec sh -c '"'"'for f; do printf "%s\t%s\t%s\n" "${f#./}" "$(stat -c %s "$f")" "$(sha256sum "$f" | cut -d" " -f1)"; done'"'"' sh {} +
  ')" || die "cannot read media volume"

EXPECTED="$expected" ACTUAL="$actual" python3 - <<'PY'
import os, sys
files, problems = {}, []
for line in os.environ["ACTUAL"].splitlines():
    if line.startswith("PERM "):
        problems.append("wrong owner/mode: " + line[5:].replace("\n", " ")); continue
    key, size, sha = line.split("\t")
    files[key] = (int(size), sha)
checked = 0
for line in os.environ["EXPECTED"].splitlines():
    if not line:
        continue
    kind, key, sha, size = line.split("\t")
    checked += 1
    got = files.get(key)
    if got is None:
        problems.append(f"missing: {key}")
    elif got[0] != int(size):
        problems.append(f"size mismatch: {key}")
    elif kind == "asset" and got[1] != sha:
        problems.append(f"sha256 mismatch: {key}")
print(f"checked {checked} DB-referenced files against the live volume")
for p in problems[:50]:
    print("PROBLEM", p)
sys.exit(1 if problems else 0)
PY
log "live media matches the database"
