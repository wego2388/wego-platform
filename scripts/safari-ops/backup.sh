#!/usr/bin/env bash
# Database backup for one Safari Tours Sharm stack.
#
#   SAFARI_COMPOSE_PROJECT=wego-safari-tours-sharm \
#   SAFARI_BACKUP_DIR=/var/backups/safari-tours-sharm \
#   SAFARI_BACKUP_GPG_RECIPIENT=<key id> [SAFARI_BACKUP_KEEP_DAYS=14] \
#   scripts/safari-ops/backup.sh
#
# Writes <stamp>.dump (pg_dump custom format, or .dump.gpg when a recipient is
# set) plus <stamp>.json with checksum, size, Flyway version and per-table row
# counts. The dump is verified with pg_restore --list before it is kept.
# Exit code is non-zero on any failure, so cron/monitoring can alert.

source "$(dirname "$0")/lib.sh"

: "${SAFARI_BACKUP_DIR:=/var/backups/safari-tours-sharm}"
: "${SAFARI_BACKUP_KEEP_DAYS:=14}"
: "${SAFARI_BACKUP_GPG_RECIPIENT:=}"

# A plaintext dump holds every customer's contact details: refuse before
# reading anything unless the operator explicitly accepts it (local drills).
if [ -z "$SAFARI_BACKUP_GPG_RECIPIENT" ] && [ "${SAFARI_ALLOW_PLAIN_BACKUP:-0}" != 1 ]; then
  die "SAFARI_BACKUP_GPG_RECIPIENT is not set; set it, or SAFARI_ALLOW_PLAIN_BACKUP=1 to keep an unencrypted dump"
fi

umask 077
mkdir -p "$SAFARI_BACKUP_DIR"
container="$(postgres_container)"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
tmp="$SAFARI_BACKUP_DIR/.${stamp}.partial"
trap 'rm -f "$tmp" "$tmp.gpg"' EXIT

started=$(date +%s)
log "backup started (project=${SAFARI_COMPOSE_PROJECT})"

# Inventory first: rows can only be added while the dump runs, never lost,
# and the drill compares with ">=" for that reason.
inventory="$(inventory_sql | container_psql "$container")"

docker exec "$container" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner' > "$tmp"
[ -s "$tmp" ] || die "pg_dump produced an empty file"

# The archive must be readable by pg_restore before it counts as a backup.
docker exec -i "$container" pg_restore --list < "$tmp" > /dev/null || die "pg_restore cannot read the new dump"

final="$SAFARI_BACKUP_DIR/${stamp}.dump"
if [ -n "$SAFARI_BACKUP_GPG_RECIPIENT" ]; then
  gpg --batch --yes --trust-model always --recipient "$SAFARI_BACKUP_GPG_RECIPIENT" --output "$tmp.gpg" --encrypt "$tmp"
  final="$final.gpg"
  mv "$tmp.gpg" "$final"
  encrypted=true
else
  log "WARNING: unencrypted dump kept on request (SAFARI_ALLOW_PLAIN_BACKUP=1, file mode 600 only)"
  mv "$tmp" "$final"
  encrypted=false
fi

sha="$(sha256sum "$final" | cut -d' ' -f1)"
size="$(stat -c %s "$final")"
commit="$(git -C "$(dirname "$0")" rev-parse --short HEAD 2>/dev/null || echo unknown)"
INVENTORY="$inventory" python3 - "$final.json" "$stamp" "$(basename "$final")" "$sha" "$size" "$encrypted" "$commit" "$SAFARI_COMPOSE_PROJECT" "$(( $(date +%s) - started ))" <<'PY'
import json, os, sys
out, stamp, name, sha, size, enc, commit, project, secs = sys.argv[1:]
tables, meta = {}, {}
for line in os.environ["INVENTORY"].splitlines():
    kind, _, rest = line.partition(":")
    if kind == "table":
        k, _, v = rest.partition("="); tables[k] = int(v)
    elif kind in ("flyway", "flyway_failed"):
        meta[kind] = rest
json.dump({"stamp": stamp, "file": name, "sha256": sha, "bytes": int(size),
           "encrypted": enc == "true", "commit": commit, "project": project,
           "seconds": int(secs), "flywayVersion": meta.get("flyway"),
           "flywayFailed": int(meta.get("flyway_failed", "0")), "rowCounts": tables},
          open(out, "w"), indent=2)
PY

# Retention: only this script's own files, only older than the window.
find "$SAFARI_BACKUP_DIR" -maxdepth 1 -type f \( -name '*.dump' -o -name '*.dump.gpg' -o -name '*.dump.json' -o -name '*.dump.gpg.json' \) \
  -mtime +"$SAFARI_BACKUP_KEEP_DAYS" -print -delete | sed 's/^/removed old backup: /' >&2 || true

log "backup ok: $(basename "$final") ${size} bytes sha256=${sha:0:12}… in $(( $(date +%s) - started ))s"
printf '%s\n' "$final"
