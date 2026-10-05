#!/usr/bin/env bash
# Restore drill: proves a backup can actually be restored, and how long it takes.
#
#   SAFARI_COMPOSE_PROJECT=wego-safari-tours-sharm \
#   SAFARI_BACKUP_DIR=/var/backups/safari-tours-sharm \
#   scripts/safari-ops/restore-drill.sh [path/to/<stamp>.dump[.gpg]]
#
# Restores into a NEW throw-away PostgreSQL container (same image as the live
# one, no published port, no shared volume) — never into the live database.
# Checks: checksum, pg_restore success, Flyway version and no failed
# migrations, and every table's row count >= the count recorded at backup
# time. Writes a timed report next to the backups and removes the container.

source "$(dirname "$0")/lib.sh"

: "${SAFARI_BACKUP_DIR:=/var/backups/safari-tours-sharm}"

dump="${1:-$(ls -1t "$SAFARI_BACKUP_DIR"/*.dump "$SAFARI_BACKUP_DIR"/*.dump.gpg 2>/dev/null | head -1 || true)}"
[ -n "$dump" ] && [ -f "$dump" ] || die "no backup file found in $SAFARI_BACKUP_DIR"
meta="$dump.json"
[ -f "$meta" ] || die "missing metadata $meta"
if python3 -c 'import json,sys;sys.exit(0 if "tours_operator_asset" in json.load(open(sys.argv[1])).get("rowCounts", {}) else 1)' "$meta"; then
  die "V29 backup requires its verified media bundle; database-only restore cannot prove managed image recovery"
fi

started=$(date +%s)
log "restore drill for $(basename "$dump")"

expected_sha="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["sha256"])' "$meta")"
[ "$(sha256sum "$dump" | cut -d' ' -f1)" = "$expected_sha" ] || die "checksum mismatch — backup file is damaged"

umask 077
work="$(mktemp -d)"
drill="safari-restore-drill-$$"
cleanup() { docker rm -f "$drill" >/dev/null 2>&1 || true; rm -rf "$work"; }
trap cleanup EXIT

plain="$dump"
if [[ "$dump" == *.gpg ]]; then
  plain="$work/restore.dump"
  gpg --batch --quiet --output "$plain" --decrypt "$dump"
fi

image="$(docker inspect -f '{{.Config.Image}}' "$(postgres_container)")"
docker run -d --name "$drill" --network none \
  -e POSTGRES_DB=drill -e POSTGRES_USER=drill -e POSTGRES_PASSWORD="drill-$$-$RANDOM" \
  --tmpfs /var/lib/postgresql:rw,size=2g "$image" >/dev/null
for _ in $(seq 60); do
  docker exec "$drill" pg_isready -U drill -d drill >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$drill" pg_isready -U drill -d drill >/dev/null || die "throw-away database did not start"
# The image's init scripts restart the server once; wait for it to settle.
sleep 2
docker exec "$drill" pg_isready -U drill -d drill >/dev/null || die "throw-away database is not ready"

restore_started=$(date +%s)
docker exec -i "$drill" pg_restore -U drill -d drill --no-owner --exit-on-error < "$plain" \
  || die "pg_restore failed"
restore_seconds=$(( $(date +%s) - restore_started ))

inventory="$(inventory_sql | container_psql "$drill")"
if [[ "$inventory" == *"table:tours_operator_asset="* ]]; then
  die "Restored schema contains V29 managed media; a database-only drill cannot validate its image volume, even if metadata omitted the asset table"
fi

report="$SAFARI_BACKUP_DIR/drill-$(date -u +%Y%m%dT%H%M%SZ).json"
INVENTORY="$inventory" python3 - "$meta" "$report" "$restore_seconds" "$(( $(date +%s) - started ))" <<'PY'
import json, os, sys
meta_path, report_path, restore_s, total_s = sys.argv[1:]
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
    problems.append("failed migrations present")
for name, count in meta["rowCounts"].items():
    if name not in tables:
        problems.append(f"table missing: {name}")
    elif tables[name] < count:
        problems.append(f"{name}: {tables[name]} rows < {count} at backup time")
report = {"backup": meta["file"], "backupStamp": meta["stamp"], "ok": not problems,
          "problems": problems, "restoreSeconds": int(restore_s), "totalSeconds": int(total_s),
          "flywayVersion": info.get("flyway"), "tablesChecked": len(meta["rowCounts"]),
          "rowsRestored": sum(tables.values())}
json.dump(report, open(report_path, "w"), indent=2)
print(json.dumps(report))
sys.exit(0 if not problems else 1)
PY
log "restore drill passed in $(( $(date +%s) - started ))s (restore ${restore_seconds}s); report: $report"
