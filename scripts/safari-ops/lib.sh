#!/usr/bin/env bash
# Shared helpers for the Safari Tours Sharm operations scripts.
# Containers are found by their Compose labels, and database name/user are read
# from the container's own environment, so no password ever appears on a
# command line or in these scripts.

set -euo pipefail

: "${SAFARI_COMPOSE_PROJECT:=wego-safari-tours-sharm}"

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

postgres_container() {
  local id
  id="$(docker ps -q \
    --filter "label=com.docker.compose.project=${SAFARI_COMPOSE_PROJECT}" \
    --filter "label=com.docker.compose.service=postgres")"
  [ -n "$id" ] || die "no running postgres container for project '${SAFARI_COMPOSE_PROJECT}'"
  [ "$(printf '%s\n' "$id" | wc -l)" -eq 1 ] || die "more than one postgres container for '${SAFARI_COMPOSE_PROJECT}'"
  printf '%s' "$id"
}

# psql inside a container over the local socket (trusted inside the container).
container_psql() {
  local container="$1"; shift
  docker exec -i "$container" sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At "$@"' psql "$@"
}

# Exact row count of every table in schema "wego" plus the latest successful
# Flyway version. Counts only — never row contents.
inventory_sql() {
  cat <<'SQL'
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
}

# One line per regular file in a (plain) media tar.gz: "sha256<TAB>size<TAB>key"
# with any leading "./" removed. Streams the archive (nothing is extracted).
# Non-zero exit if the archive cannot be read to the end (truncated/corrupt).
archive_file_map() {
  python3 - "$1" <<'PY'
import hashlib, sys, tarfile
with tarfile.open(sys.argv[1], "r:gz") as tf:
    for m in tf:
        # The runbook extracts as root: refuse anything but plain files and
        # directories, and any absolute or parent-escaping name.
        parts = m.name.split("/")
        if m.name.startswith("/") or ".." in parts:
            sys.exit(f"unsafe archive member name: {m.name!r}")
        if m.isdir():
            continue
        if not m.isreg():
            sys.exit(f"unsupported archive member (link/device/fifo): {m.name!r}")
        h, n = hashlib.sha256(), 0
        f = tf.extractfile(m)
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk); n += len(chunk)
        name = m.name[2:] if m.name.startswith("./") else m.name
        print(f"{h.hexdigest()}\t{n}\t{name}")
PY
}
