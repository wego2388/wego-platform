#!/usr/bin/env bash
# Consistent DB + media bundle backup for Safari Tours Sharm (V29 and later).
#
# Usage:
#   SAFARI_COMPOSE_PROJECT=wego-safari-media-final \
#   SAFARI_BACKUP_DIR=/var/backups/safari-tours-sharm \
#   SAFARI_BACKUP_GPG_RECIPIENT=<key id> \
#   [SAFARI_BACKUP_KEEP_DAYS=14] \
#   scripts/safari-ops/bundle-backup.sh
#
# Produces inside SAFARI_BACKUP_DIR:
#   <stamp>.bundle/
#     db.dump          – pg_dump custom-format (or db.dump.gpg)
#     media.tar.gz     – media volume contents (or media.tar.gz.gpg)
#     manifest.json    – sha256+size of db and media files, sha256+size of EVERY
#                        file inside the media archive ("files"), DB row counts,
#                        Flyway version, and the DB-referenced storage keys
#
# The DB and media are taken as close together as possible (no pause, no lock
# of the live service). Every DB-referenced storage_key (assets + variants) must
# be present in the archive or the backup aborts. bundle-restore-drill.sh later
# re-derives the expected keys from the RESTORED database, not from the manifest,
# and checks every archived file against the per-file hashes recorded here.
# A fresh V29 database with zero asset rows is valid (empty key list).
# Exit code is non-zero on any failure.

source "$(dirname "$0")/lib.sh"

: "${SAFARI_BACKUP_DIR:=/var/backups/safari-tours-sharm}"
: "${SAFARI_BACKUP_KEEP_DAYS:=14}"
: "${SAFARI_BACKUP_GPG_RECIPIENT:=}"

if [ -z "$SAFARI_BACKUP_GPG_RECIPIENT" ] && [ "${SAFARI_ALLOW_PLAIN_BACKUP:-0}" != 1 ]; then
  die "SAFARI_BACKUP_GPG_RECIPIENT is not set; set it, or SAFARI_ALLOW_PLAIN_BACKUP=1 to keep an unencrypted bundle"
fi

umask 077
mkdir -p "$SAFARI_BACKUP_DIR"
container="$(postgres_container)"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
bundle_dir="$SAFARI_BACKUP_DIR/${stamp}.bundle"
tmp_dir="$SAFARI_BACKUP_DIR/.${stamp}.partial"
trap 'rm -rf "$tmp_dir"' EXIT
mkdir -p "$tmp_dir"

started=$(date +%s)
log "bundle backup started (project=${SAFARI_COMPOSE_PROJECT}, stamp=${stamp})"

# ── 1. Snapshot DB-referenced media keys BEFORE dump (monotonic growth only).
log "collecting DB asset inventory..."
schema_ok="$(printf '%s\n' \
  "SELECT to_regclass('wego.tours_operator_asset') IS NOT NULL AND to_regclass('wego.tours_operator_asset_variant') IS NOT NULL" \
  | container_psql "$container")" || die "asset schema check query failed"
[ "$schema_ok" = "t" ] || die "V29 asset tables are missing; this script only supports V29 and later"
# A failed query is fatal (stderr kept); an empty result is valid (no uploads yet).
db_asset_keys="$(printf '%s\n' \
  "SELECT storage_key FROM wego.tours_operator_asset
   UNION ALL
   SELECT storage_key FROM wego.tours_operator_asset_variant
   ORDER BY 1" | container_psql "$container")" || die "failed to read asset keys from DB"

# ── 2. DB dump.
log "dumping database..."
db_tmp="$tmp_dir/db.dump"
docker exec "$container" sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner' > "$db_tmp"
[ -s "$db_tmp" ] || die "pg_dump produced an empty file"
docker exec -i "$container" pg_restore --list < "$db_tmp" > /dev/null \
  || die "pg_restore cannot read the new db dump"
log "db dump ok ($(stat -c %s "$db_tmp") bytes)"

# ── 3. DB inventory for manifest row counts.
inventory="$(inventory_sql | container_psql "$container")"

# ── 4. Media volume archive.
log "archiving media volume..."
# Find the named volume used by this compose project for media.
media_volume="$(docker volume ls -q \
  --filter "label=com.docker.compose.project=${SAFARI_COMPOSE_PROJECT}" \
  | grep -i media | head -1 || true)"
if [ -z "$media_volume" ]; then
  # Fallback: derive from project name convention.
  media_volume="${SAFARI_COMPOSE_PROJECT}-media"
fi
volume_exists="$(docker volume ls -q --filter "name=^${media_volume}$")"
[ -n "$volume_exists" ] || die "media volume '${media_volume}' not found"

media_tmp="$tmp_dir/media.tar.gz"
# Run tar in a throwaway container with the volume mounted read-only.
docker run --rm \
  --volume "${media_volume}:/data/media:ro" \
  --volume "$tmp_dir:/out" \
  --network none \
  -e HOST_UID="$(id -u)" -e HOST_GID="$(id -g)" \
  public.ecr.aws/docker/library/alpine:3.20 \
  sh -c 'set -e; cd /data/media; tar -czf /out/media.tar.gz .; chown "$HOST_UID:$HOST_GID" /out/media.tar.gz; chmod 600 /out/media.tar.gz' \
  || die "tar of media volume failed (disk full or unreadable files); backup aborted"
[ -s "$media_tmp" ] || die "media tar archive is empty (volume may be empty or inaccessible)"
log "media archive ok ($(stat -c %s "$media_tmp") bytes)"

# ── 5. Verify every DB-referenced key is present in the archive.
log "cross-checking DB keys against archive..."
files_tsv="$tmp_dir/files.tsv"
archive_file_map "$media_tmp" > "$files_tsv" || die "cannot read back media archive"
archive_keys="$(cut -f3 "$files_tsv")"
missing_count=0
while IFS= read -r key; do
  [ -z "$key" ] && continue
  if ! printf '%s\n' "$archive_keys" | grep -qxF -- "$key"; then
    log "MISSING in archive: $key"
    (( missing_count++ )) || true
  fi
done <<< "$db_asset_keys"
if [ "$missing_count" -gt 0 ]; then
  die "bundle aborted: $missing_count DB-referenced file(s) missing from media archive"
fi
log "cross-check passed: all DB-referenced keys found in archive ($(wc -l < "$files_tsv") files hashed)"

# ── 6. Optionally encrypt.
encrypt_file() {
  local src="$1" dst="$2"
  if [ -n "$SAFARI_BACKUP_GPG_RECIPIENT" ]; then
    gpg --batch --yes --trust-model always \
      --recipient "$SAFARI_BACKUP_GPG_RECIPIENT" \
      --output "$dst" --encrypt "$src"
    rm -f "$src"
  else
    mv "$src" "$dst"
  fi
}

# Build the bundle inside the private temp dir and move it into place only
# when complete, so a failure never leaves an empty or partial *.bundle.
stage_dir="$tmp_dir/bundle"
mkdir -p "$stage_dir"
chmod 700 "$stage_dir"
final_db="$stage_dir/db.dump"
final_media="$stage_dir/media.tar.gz"
encrypted=false

if [ -n "$SAFARI_BACKUP_GPG_RECIPIENT" ]; then
  log "encrypting..."
  encrypt_file "$db_tmp"    "$stage_dir/db.dump.gpg"
  encrypt_file "$media_tmp" "$stage_dir/media.tar.gz.gpg"
  final_db="$stage_dir/db.dump.gpg"
  final_media="$stage_dir/media.tar.gz.gpg"
  encrypted=true
else
  log "WARNING: unencrypted bundle kept on request (SAFARI_ALLOW_PLAIN_BACKUP=1)"
  mv "$db_tmp"    "$final_db"
  mv "$media_tmp" "$final_media"
fi
chmod 600 "$final_db" "$final_media"

# ── 7. Manifest with checksums + DB inventory + asset key list.
db_sha="$(sha256sum "$final_db"    | cut -d' ' -f1)"
media_sha="$(sha256sum "$final_media" | cut -d' ' -f1)"
commit="$(git -C "$(dirname "$0")" rev-parse --short HEAD 2>/dev/null || echo unknown)"

INVENTORY="$inventory" \
DB_ASSET_KEYS="$db_asset_keys" \
FILES_TSV="$files_tsv" \
python3 - \
    "$stage_dir/manifest.json" \
    "$stamp" \
    "$(basename "$final_db")" "$db_sha" "$(stat -c %s "$final_db")" \
    "$(basename "$final_media")" "$media_sha" "$(stat -c %s "$final_media")" \
    "$encrypted" "$commit" "$SAFARI_COMPOSE_PROJECT" \
    "$(( $(date +%s) - started ))" <<'PY'
import json, os, sys
(out, stamp, db_name, db_sha, db_size,
 media_name, media_sha, media_size,
 enc, commit, project, secs) = sys.argv[1:]
tables, meta = {}, {}
for line in os.environ["INVENTORY"].splitlines():
    kind, _, rest = line.partition(":")
    if kind == "table":
        k, _, v = rest.partition("="); tables[k] = int(v)
    elif kind in ("flyway", "flyway_failed"):
        meta[kind] = rest
files = {}
for line in open(os.environ["FILES_TSV"]):
    sha, size, key = line.rstrip("\n").split("\t", 2)
    files[key] = {"sha256": sha, "bytes": int(size)}
asset_keys = [k for k in os.environ["DB_ASSET_KEYS"].splitlines() if k]
json.dump({
    "stamp": stamp,
    "db": {"file": db_name, "sha256": db_sha, "bytes": int(db_size)},
    "media": {"file": media_name, "sha256": media_sha, "bytes": int(media_size)},
    "encrypted": enc == "true",
    "commit": commit, "project": project, "seconds": int(secs),
    "flywayVersion": meta.get("flyway"),
    "flywayFailed": int(meta.get("flyway_failed", "0")),
    "rowCounts": tables,
    "dbAssetKeys": sorted(set(asset_keys)),
    "files": dict(sorted(files.items())),
}, open(out, "w"), indent=2)
PY

mv "$stage_dir" "$bundle_dir"

# ── 8. Retention.
find "$SAFARI_BACKUP_DIR" -maxdepth 1 -type d -name '*.bundle' \
  -mtime +"$SAFARI_BACKUP_KEEP_DAYS" -print | while read -r old; do
    log "removing old bundle: $old"
    rm -rf "$old"
done

elapsed=$(( $(date +%s) - started ))
log "bundle backup ok: ${stamp}.bundle in ${elapsed}s (db sha256=${db_sha:0:12}… media sha256=${media_sha:0:12}…)"
printf '%s\n' "$bundle_dir"
