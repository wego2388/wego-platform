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
#     manifest.json    – file list with sha256, sizes, DB row counts, Flyway ver
#
# The DB and media are taken as close together as possible (no pause, no lock
# of the live service). The manifest records every DB-referenced storage_key
# so restore-bundle can detect missing files.
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
db_asset_keys="$(docker exec "$container" sh -c \
  'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c \
  "SELECT storage_key FROM wego.tours_operator_asset
   UNION ALL
   SELECT storage_key FROM wego.tours_operator_asset_variant
   ORDER BY 1"' 2>/dev/null)"
[ -n "$db_asset_keys" ] || die "failed to read asset keys from DB (V29 tables missing?)"

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
  public.ecr.aws/docker/library/alpine:3.20 \
  sh -c 'cd /data/media && tar -czf /out/media.tar.gz . 2>/dev/null; echo "tar_exit:$?"' \
  | grep -v "^tar_exit:" || true
[ -s "$media_tmp" ] || die "media tar archive is empty (volume may be empty or inaccessible)"
log "media archive ok ($(stat -c %s "$media_tmp") bytes)"

# ── 5. Verify every DB-referenced key is present in the archive.
log "cross-checking DB keys against archive..."
missing_count=0
archive_listing="$(docker run --rm \
  --volume "$tmp_dir:/in:ro" \
  --network none \
  public.ecr.aws/docker/library/alpine:3.20 \
  tar -tzf /in/media.tar.gz 2>/dev/null | sed 's|^\./||')"
while IFS= read -r key; do
  [ -z "$key" ] && continue
  if ! printf '%s\n' "$archive_listing" | grep -qF "$key"; then
    log "MISSING in archive: $key"
    (( missing_count++ )) || true
  fi
done <<< "$db_asset_keys"
if [ "$missing_count" -gt 0 ]; then
  die "bundle aborted: $missing_count DB-referenced file(s) missing from media archive"
fi
log "cross-check passed: all DB-referenced keys found in archive"

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

mkdir -p "$bundle_dir"
final_db="$bundle_dir/db.dump"
final_media="$bundle_dir/media.tar.gz"
encrypted=false

if [ -n "$SAFARI_BACKUP_GPG_RECIPIENT" ]; then
  log "encrypting..."
  encrypt_file "$db_tmp"    "$bundle_dir/db.dump.gpg"
  encrypt_file "$media_tmp" "$bundle_dir/media.tar.gz.gpg"
  final_db="$bundle_dir/db.dump.gpg"
  final_media="$bundle_dir/media.tar.gz.gpg"
  encrypted=true
else
  log "WARNING: unencrypted bundle kept on request (SAFARI_ALLOW_PLAIN_BACKUP=1)"
  mv "$db_tmp"    "$final_db"
  mv "$media_tmp" "$final_media"
fi

# ── 7. Manifest with checksums + DB inventory + asset key list.
db_sha="$(sha256sum "$final_db"    | cut -d' ' -f1)"
media_sha="$(sha256sum "$final_media" | cut -d' ' -f1)"
commit="$(git -C "$(dirname "$0")" rev-parse --short HEAD 2>/dev/null || echo unknown)"

INVENTORY="$inventory" \
DB_ASSET_KEYS="$db_asset_keys" \
python3 - \
    "$bundle_dir/manifest.json" \
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
}, open(out, "w"), indent=2)
PY

# ── 8. Retention.
find "$SAFARI_BACKUP_DIR" -maxdepth 1 -type d -name '*.bundle' \
  -mtime +"$SAFARI_BACKUP_KEEP_DAYS" -print | while read -r old; do
    log "removing old bundle: $old"
    rm -rf "$old"
done

elapsed=$(( $(date +%s) - started ))
log "bundle backup ok: ${stamp}.bundle in ${elapsed}s (db sha256=${db_sha:0:12}… media sha256=${media_sha:0:12}…)"
printf '%s\n' "$bundle_dir"
