#!/usr/bin/env bash
# Health check for one Safari Tours Sharm stack — run from cron every 5 minutes.
#
#   SAFARI_COMPOSE_PROJECT=wego-safari-tours-sharm \
#   SAFARI_HEALTH_URL=http://127.0.0.1:58085 \
#   SAFARI_BACKUP_DIR=/var/backups/safari-tours-sharm \
#   [SAFARI_PUBLIC_HOST=example.com] [SAFARI_ALERT_COMMAND='/usr/local/bin/notify'] \
#   scripts/safari-ops/health-check.sh
#
# Prints one line per check (OK / WARN / FAIL). Exit 1 when anything FAILs.
# When SAFARI_ALERT_COMMAND is set, the WARN/FAIL lines are piped to it.
# Output carries no customer data: only service names, ages, sizes and counts.

source "$(dirname "$0")/lib.sh"
set +e

: "${SAFARI_HEALTH_URL:=http://127.0.0.1:58085}"
: "${SAFARI_BACKUP_DIR:=/var/backups/safari-tours-sharm}"
: "${SAFARI_BACKUP_MAX_AGE_HOURS:=26}"
: "${SAFARI_DRILL_MAX_AGE_DAYS:=8}"
: "${SAFARI_DISK_MAX_PERCENT:=85}"
: "${SAFARI_CERT_MIN_DAYS:=14}"
: "${SAFARI_PUBLIC_HOST:=}"
: "${SAFARI_ALERT_COMMAND:=}"

results=()
ok()   { results+=("OK   $1"); }
warn() { results+=("WARN $1"); }
fail() { results+=("FAIL $1"); }

# 1. Edge and backend health through the public entry point.
if body="$(curl -fsS --max-time 10 "$SAFARI_HEALTH_URL/healthz" 2>/dev/null)" && grep -q '"status":"UP"' <<<"$body"; then
  ok "edge /healthz UP"
else
  fail "edge /healthz not UP at $SAFARI_HEALTH_URL"
fi

# 2. Every container of the project healthy, with no restart loop.
mapfile -t containers < <(docker ps -a -q --filter "label=com.docker.compose.project=${SAFARI_COMPOSE_PROJECT}")
if [ "${#containers[@]}" -eq 0 ]; then
  fail "no containers for project ${SAFARI_COMPOSE_PROJECT}"
else
  for c in "${containers[@]}"; do
    read -r name state health restarts < <(docker inspect -f \
      '{{index .Config.Labels "com.docker.compose.service"}} {{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} {{.RestartCount}}' "$c")
    if [ "$state" != running ] || { [ "$health" != healthy ] && [ "$health" != none ]; }; then
      fail "container $name is $state/$health"
    elif [ "$restarts" -gt 3 ]; then
      warn "container $name restarted $restarts times"
    else
      ok "container $name $state/$health"
    fi
  done
fi

# 3. Online sales switch (a pause is deliberate, but nobody should forget it).
if status="$(curl -fsS --max-time 10 "$SAFARI_HEALTH_URL/api/v1/tours-operator/sales-status" 2>/dev/null)"; then
  if grep -q '"bookingsOpen":false\|"paymentsOpen":false' <<<"$status"; then
    warn "online sales are PAUSED (ERP → Online sales)"
  else
    ok "online sales open"
  fi
else
  warn "sales status not readable"
fi

# 4. Backup freshness and the last restore drill.
# A DB+media bundle counts only once its manifest exists (bundles are moved
# into place complete); older DB-only dumps still count for pre-V29 installs.
latest="$(ls -1td "$SAFARI_BACKUP_DIR"/*.bundle/manifest.json "$SAFARI_BACKUP_DIR"/*.dump "$SAFARI_BACKUP_DIR"/*.dump.gpg 2>/dev/null | head -1)"
if [ -z "$latest" ]; then
  fail "no backup in $SAFARI_BACKUP_DIR"
else
  age_h=$(( ( $(date +%s) - $(stat -c %Y "$latest") ) / 3600 ))
  if [ "$age_h" -gt "$SAFARI_BACKUP_MAX_AGE_HOURS" ]; then
    fail "latest backup is ${age_h}h old"
  else
    ok "latest backup ${age_h}h old"
  fi
fi
drill="$(ls -1t "$SAFARI_BACKUP_DIR"/bundle-drill-*.json "$SAFARI_BACKUP_DIR"/drill-*.json 2>/dev/null | head -1)"
if [ -z "$drill" ]; then
  warn "no restore drill on record"
else
  drill_age_d=$(( ( $(date +%s) - $(stat -c %Y "$drill") ) / 86400 ))
  if ! grep -q '"ok": true' "$drill"; then
    fail "last restore drill FAILED ($(basename "$drill"))"
  elif [ "$drill_age_d" -gt "$SAFARI_DRILL_MAX_AGE_DAYS" ]; then
    warn "last restore drill is ${drill_age_d} days old"
  else
    ok "last restore drill passed ${drill_age_d}d ago"
  fi
fi

# 5. Disk space where the database and the backups live.
for path in "$SAFARI_BACKUP_DIR" "$(docker info -f '{{.DockerRootDir}}' 2>/dev/null || echo /)"; do
  [ -d "$path" ] || continue
  used="$(df -P "$path" | awk 'NR==2 {gsub("%","",$5); print $5}')"
  if [ "$used" -ge "$SAFARI_DISK_MAX_PERCENT" ]; then fail "disk ${used}% used at $path"; else ok "disk ${used}% used at $path"; fi
done

# 6. TLS certificate expiry (only once a public domain exists).
if [ -n "$SAFARI_PUBLIC_HOST" ]; then
  end="$(echo | openssl s_client -servername "$SAFARI_PUBLIC_HOST" -connect "$SAFARI_PUBLIC_HOST:443" 2>/dev/null \
    | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)"
  if [ -z "$end" ]; then
    fail "cannot read TLS certificate of $SAFARI_PUBLIC_HOST"
  else
    days=$(( ( $(date -d "$end" +%s) - $(date +%s) ) / 86400 ))
    if [ "$days" -lt "$SAFARI_CERT_MIN_DAYS" ]; then fail "TLS certificate expires in ${days} days"; else ok "TLS certificate valid ${days} more days"; fi
  fi
fi

printf '%s\n' "${results[@]}"
problems="$(printf '%s\n' "${results[@]}" | grep -E '^(WARN|FAIL)')"
if [ -n "$problems" ] && [ -n "$SAFARI_ALERT_COMMAND" ]; then
  printf 'Safari Tours Sharm (%s):\n%s\n' "$(hostname)" "$problems" | sh -c "$SAFARI_ALERT_COMMAND" \
    || echo "WARN alert command failed" >&2
fi
grep -q '^FAIL' <<<"$problems" && exit 1
exit 0
