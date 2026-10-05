#!/usr/bin/env bash
# Bundle restore drill for Safari Tours Sharm (V29 and later).
#
# Usage:
#   SAFARI_COMPOSE_PROJECT=wego-safari-media-final \
#   SAFARI_BACKUP_DIR=/var/backups/safari-tours-sharm \
#   scripts/safari-ops/bundle-restore-drill.sh [path/to/<stamp>.bundle]
#
# Validates:
#   1. manifest.json sha256 for both db and media files
#   2. pg_restore into a throw-away PostgreSQL container (no published port,
#      no shared volume, --network none)
#   3. Flyway version and zero failed migrations
#   4. Row counts >= counts recorded at backup time
#   5. Every dbAssetKeys entry exists in the media archive
#   6. Every dbAssetKeys entry in the archive matches the sha256 recorded in
#      the DB's tours_operator_asset table (detects bit-rot or replacement)
#
# The drill does NOT write to any live volume. It proves both DB and media
# can be reconstructed together and cross-reference is intact.
# Exit code non-zero on any failure; report appended to SAFARI_BACKUP_DIR.

source "$(dirname "$0")/lib.sh"

: "${SAFARI_BACKUP_DIR:=/var/backups/safari-tours-sharm}"

# ── Locate bundle.
bundle="${1:-$(ls -1td "$SAFARI_BACKUP_DIR"/*.bundle 2>/dev/null | head -1 || true)}"
[ -n "$bundle" ] && [ -d "$bundle" ] || die "no bundle directory found in $SAFARI_BACKUP_DIR"
manifest="$bundle/manifest.json"
[ -f "$manifest" ] || die "missing manifest: $manifest"

log "restore drill for $(basename "$bundle")"
started=$(date +%s)

# ── Locate db and media files (plain or encrypted).
db_file=""
for f in "$bundle/db.dump.gpg" "$bundle/db.dump"; do
  [ -f "$f" ] && { db_file="$f"; break; }
done
[ -n "$db_file" ] || die "no db dump found in $bundle"

media_file=""
for f in "$bundle/media.tar.gz.gpg" "$bundle/media.tar.gz"; do
  [ -f "$f" ] && { media_file="$f"; break; }
done
[ -n "$media_file" ] || die "no media archive found in $bundle"

# ── 1. Checksum verification.
expected_db_sha="$(python3 -c \
  'import json,sys; print(json.load(open(sys.argv[1]))["db"]["sha256"])' "$manifest")"
expected_media_sha="$(python3 -c \
  'import json,sys; print(json.load(open(sys.argv[1]))["media"]["sha256"])' "$manifest")"

actual_db_sha="$(sha256sum "$db_file" | cut -d' ' -f1)"
actual_media_sha="$(sha256sum "$media_file" | cut -d' ' -f1)"

[ "$actual_db_sha"    = "$expected_db_sha"    ] || die "DB dump checksum mismatch — backup is damaged"
[ "$actual_media_sha" = "$expected_media_sha" ] || die "media archive checksum mismatch — backup is damaged"
log "checksums ok"

# ── 2. Decrypt if needed.
umask 077
work="$(mktemp -d)"
trap 'rm -rf "$work"; docker rm -f "$drill_name" >/dev/null 2>&1 || true' EXIT

plain_db="$db_file"
plain_media="$media_file"

if [[ "$db_file" == *.gpg ]]; then
  plain_db="$work/db.dump"
  gpg --batch --quiet --output "$plain_db" --decrypt "$db_file"
fi
if [[ "$media_file" == *.gpg ]]; then
  plain_media="$work/media.tar.gz"
  gpg --batch --quiet --output "$plain_media" --decrypt "$media_file"
fi

# ── 3. DB restore into throw-away container.
drill_name="safari-bundle-drill-$$"
image="$(docker inspect -f '{{.Config.Image}}' "$(postgres_container)")"
docker run -d --name "$drill_name" --network none \
  -e POSTGRES_DB=drill -e POSTGRES_USER=drill -e POSTGRES_PASSWORD="drill-$$-$RANDOM" \
  --tmpfs /var/lib/postgresql:rw,size=2g "$image" >/dev/null
for _ in $(seq 60); do
  docker exec "$drill_name" pg_isready -U drill -d drill >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$drill_name" pg_isready -U drill -d drill >/dev/null || die "throw-away DB did not start"
sleep 2
docker exec "$drill_name" pg_isready -U drill -d drill >/dev/null || die "throw-away DB not ready after settle"

restore_started=$(date +%s)
docker exec -i "$drill_name" pg_restore -U drill -d drill --no-owner --exit-on-error \
  < "$plain_db" || die "pg_restore failed"
restore_seconds=$(( $(date +%s) - restore_started ))
log "pg_restore completed in ${restore_seconds}s"

# ── 4. DB inventory from restored database.
inventory_sql_file="$work/inventory.sql"
cat > "$inventory_sql_file" <<'SQL'
SELECT 'table:' || table_name || '=' ||
       (xpath('/row/c/text()',
              query_to_xml(format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name),
                           false, true, '')))[1]::text
FROM information_schema.tables
WHERE table_schema = 'wego' AND table_type = 'BASE TABLE'
ORDER BY table_name;
SELECT 'flyway:' || coalesce(max(version::int)::text, 'none')
FROM public.flyway_schema_history WHERE success;
SELECT 'flyway_failed:' || count(*) FROM public.flyway_schema_history WHERE NOT success;
SQL
inventory="$(docker exec -i "$drill_name" psql -U drill -d drill -At < "$inventory_sql_file" 2>/dev/null)"
[ -n "$inventory" ] || die "failed to read inventory from restored database"

# ── 5. Cross-check: every manifest dbAssetKey must exist in media archive.
log "cross-checking media archive against DB asset keys..."
archive_listing="$(docker run --rm \
  --volume "$work:/in:ro" \
  --network none \
  public.ecr.aws/docker/library/alpine:3.20 \
  tar -tzf /in/$(basename "$plain_media") 2>/dev/null | sed 's|^\./||')" \
  || archive_listing="$(tar -tzf "$plain_media" 2>/dev/null | sed 's|^\./||')"

manifest_keys="$(python3 -c \
  'import json,sys; [print(k) for k in json.load(open(sys.argv[1])).get("dbAssetKeys",[])]' "$manifest")"

missing_keys=()
while IFS= read -r key; do
  [ -z "$key" ] && continue
  if ! printf '%s\n' "$archive_listing" | grep -qF "$key"; then
    missing_keys+=("$key")
    log "MISSING from archive: $key"
  fi
done <<< "$manifest_keys"

if [ "${#missing_keys[@]}" -gt 0 ]; then
  die "bundle integrity failed: ${#missing_keys[@]} DB-referenced file(s) missing from media archive"
fi
key_count="$(printf '%s\n' "$manifest_keys" | grep -c . || echo 0)"
log "media cross-check passed: all ${key_count} keys present"

# ── 6. Verify original-file sha256s from restored DB match archive.
#       Only originals (no _w360/_w768 suffix) have sha256 in tours_operator_asset.
log "verifying original file sha256 hashes..."
db_sha_map="$(docker exec "$drill_name" psql -U drill -d drill -At -c \
  "SELECT storage_key || '|' || sha256 FROM wego.tours_operator_asset ORDER BY storage_key" 2>/dev/null || true)"

sha_failures=0
# Extract originals from the archive into a temp subdir for hashing.
originals_dir="$work/originals"
mkdir -p "$originals_dir"

while IFS='|' read -r key db_sha256; do
  [ -z "$key" ] && continue
  # Extract this single file from the archive.
  tar -xzf "$plain_media" -C "$originals_dir" --transform 's|.*/||' "./${key}" 2>/dev/null \
    || tar -xzf "$plain_media" -C "$originals_dir" --transform 's|.*/||' "${key}" 2>/dev/null \
    || { log "CANNOT EXTRACT: $key"; (( sha_failures++ )) || true; continue; }
  extracted_file="$originals_dir/$(basename "$key")"
  if [ -f "$extracted_file" ]; then
    actual_sha="$(sha256sum "$extracted_file" | cut -d' ' -f1)"
    if [ "$actual_sha" != "$db_sha256" ]; then
      log "SHA256 MISMATCH: $key — DB has $db_sha256, archive has $actual_sha"
      (( sha_failures++ )) || true
    fi
    rm -f "$extracted_file"
  else
    log "CANNOT FIND extracted file for: $key"
    (( sha_failures++ )) || true
  fi
done <<< "$db_sha_map"

if [ "$sha_failures" -gt 0 ]; then
  die "bundle integrity failed: $sha_failures original file(s) have sha256 mismatch or could not be extracted"
fi
log "sha256 verification passed for all originals"

# ── 7. Build final report.
report="$SAFARI_BACKUP_DIR/bundle-drill-$(date -u +%Y%m%dT%H%M%SZ).json"
INVENTORY="$inventory" \
python3 - "$manifest" "$report" "$restore_seconds" "$(( $(date +%s) - started ))" \
  "${#missing_keys[@]}" "$sha_failures" <<'PY'
import json, os, sys
meta_path, report_path, restore_s, total_s, missing_k, sha_fail = sys.argv[1:]
meta = json.load(open(meta_path))
tables, info = {}, {}
for line in os.environ["INVENTORY"].splitlines():
    kind, _, rest = line.partition(":")
    if kind == "table":
        k, _, v = rest.partition("="); tables[k] = int(v)
    else:
        info[kind] = rest
problems = []
if info.get("flyway") != meta.get("flywayVersion"):
    problems.append(f"flyway version {info.get('flyway')} != {meta.get('flywayVersion')}")
if info.get("flyway_failed", "0") != "0":
    problems.append("failed migrations present after restore")
for name, count in meta["rowCounts"].items():
    if name not in tables:
        problems.append(f"table missing after restore: {name}")
    elif tables[name] < count:
        problems.append(f"{name}: {tables[name]} rows < {count} at backup time")
if int(missing_k) > 0:
    problems.append(f"{missing_k} DB-referenced media file(s) missing from archive")
if int(sha_fail) > 0:
    problems.append(f"{sha_fail} original file(s) failed sha256 verification")
report = {
    "bundle": os.path.basename(meta_path.replace("/manifest.json", "")),
    "backupStamp": meta["stamp"],
    "ok": not problems,
    "problems": problems,
    "restoreSeconds": int(restore_s),
    "totalSeconds": int(total_s),
    "flywayVersion": info.get("flyway"),
    "tablesChecked": len(meta["rowCounts"]),
    "rowsRestored": sum(tables.values()),
    "mediaKeysChecked": len(meta.get("dbAssetKeys", [])),
}
json.dump(report, open(report_path, "w"), indent=2)
print(json.dumps(report))
sys.exit(0 if not problems else 1)
PY
log "restore drill completed in $(( $(date +%s) - started ))s; report: $report"
