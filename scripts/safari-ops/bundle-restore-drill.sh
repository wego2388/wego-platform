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
#   5. Expected keys come from the RESTORED database (assets + variants), never
#      from the manifest; every one must exist in the media archive
#   6. EVERY file in the archive (originals and variants) matches the sha256 and
#      size recorded in manifest.json at backup time, and the archive holds no
#      file the manifest does not list (and vice versa)
#   7. Originals also match tours_operator_asset.sha256/file_size_bytes, and
#      variants match tours_operator_asset_variant.file_size_bytes
#   An empty asset table (fresh V29 DB) is valid; an unreadable one is not.
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
drill_name=""
trap 'rm -rf "$work"; [ -z "$drill_name" ] || docker rm -f "$drill_name" >/dev/null 2>&1 || true' EXIT

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

# ── 5. Expected keys come from the RESTORED DB; verify every archived file.
log "verifying media archive against restored DB and manifest hashes..."
db_files_tsv="$work/db-files.tsv"
printf '%s\n' \
  "SELECT 'asset' || chr(9) || storage_key || chr(9) || sha256 || chr(9) || file_size_bytes FROM wego.tours_operator_asset
   UNION ALL
   SELECT 'variant' || chr(9) || storage_key || chr(9) || '-' || chr(9) || file_size_bytes FROM wego.tours_operator_asset_variant
   ORDER BY 1" \
  | docker exec -i "$drill_name" psql -X -v ON_ERROR_STOP=1 -U drill -d drill -At > "$db_files_tsv" \
  || die "failed to read asset/variant rows from restored database"
asset_rows="$(printf '%s\n' "SELECT count(*) FROM wego.tours_operator_asset" \
  | docker exec -i "$drill_name" psql -X -v ON_ERROR_STOP=1 -U drill -d drill -At)" \
  || die "failed to count asset rows in restored database"
if [ "$asset_rows" -gt 0 ] && [ ! -s "$db_files_tsv" ]; then
  die "restored DB has $asset_rows asset row(s) but the key/sha map is empty"
fi

archive_tsv="$work/archive-files.tsv"
archive_file_map "$plain_media" > "$archive_tsv" || die "media archive cannot be read to the end (corrupt or truncated)"

verify_out="$work/verify.out"
python3 - "$manifest" "$db_files_tsv" "$archive_tsv" > "$verify_out" <<'PY' || true
import json, sys
manifest = json.load(open(sys.argv[1]))
mfiles = manifest.get("files")
problems = []
missing = mismatch = 0
if not isinstance(mfiles, dict):
    print("PROBLEM manifest has no per-file 'files' map (old or damaged manifest)")
    print("COUNTS 1 0 0"); sys.exit(0)
arch = {}
for line in open(sys.argv[3]):
    sha, size, key = line.rstrip("\n").split("\t", 2)
    arch[key] = (sha, int(size))
db = []
for line in open(sys.argv[2]):
    line = line.rstrip("\n")
    if line:
        kind, key, sha, size = line.split("\t")
        db.append((kind, key, sha, int(size)))
for kind, key, sha, size in db:
    if key not in arch:
        missing += 1; print(f"PROBLEM MISSING from archive: {key}"); continue
    a_sha, a_size = arch[key]
    if kind == "asset" and a_sha != sha:
        mismatch += 1; print(f"PROBLEM SHA256 MISMATCH (DB): {key} db={sha} archive={a_sha}")
    if a_size != size:
        mismatch += 1; print(f"PROBLEM SIZE MISMATCH (DB): {key} db={size} archive={a_size}")
for key, (a_sha, a_size) in arch.items():
    m = mfiles.get(key)
    if m is None:
        mismatch += 1; print(f"PROBLEM archive file not in manifest: {key}")
    elif m["sha256"] != a_sha or m["bytes"] != a_size:
        mismatch += 1; print(f"PROBLEM MANIFEST HASH/SIZE MISMATCH: {key}")
for key in mfiles:
    if key not in arch:
        mismatch += 1; print(f"PROBLEM manifest file not in archive: {key}")
print(f"COUNTS 0 {missing} {mismatch} {len(db)} {len(arch)}")
PY
{ grep '^PROBLEM' "$verify_out" || true; } | sed 's/^PROBLEM //' | while IFS= read -r l; do log "$l"; done
counts="$(grep '^COUNTS' "$verify_out" || true)"
[ -n "$counts" ] || die "media verification did not complete"
read -r _ bad_manifest missing_count sha_failures key_count archive_count <<< "$counts"
if [ "$bad_manifest" != 0 ] || [ "$missing_count" != 0 ] || [ "$sha_failures" != 0 ]; then
  die "bundle integrity failed: manifest_problem=${bad_manifest} missing=${missing_count} hash_or_size_mismatch=${sha_failures}"
fi
log "media verification passed: ${key_count} DB keys present; ${archive_count} archived files match manifest hashes"

# ── 7. Build final report.
report="$SAFARI_BACKUP_DIR/bundle-drill-$(date -u +%Y%m%dT%H%M%SZ).json"
INVENTORY="$inventory" \
python3 - "$manifest" "$report" "$restore_seconds" "$(( $(date +%s) - started ))" \
  "$missing_count" "$sha_failures" <<'PY'
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
    problems.append(f"{sha_fail} media file(s) failed sha256/size verification")
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
    "mediaFilesVerified": len(meta.get("files", {})),
}
json.dump(report, open(report_path, "w"), indent=2)
print(json.dumps(report))
sys.exit(0 if not problems else 1)
PY
log "restore drill completed in $(( $(date +%s) - started ))s; report: $report"
