#!/usr/bin/env bash
set -euo pipefail

# Dumps the real Sharm To Go Postgres database (the `postgres` service in
# infrastructure/compose/sharm-to-go.compose.yaml) to a local, timestamped,
# pg_dump custom-format file — the format sharm-to-go-restore-drill.sh (and
# a real `pg_restore`) expects. Prune-by-count only; this is a LOCAL backup,
# not an off-box one. Off-box storage (S3, rsync to a second box, etc.) is
# still an open owner decision — see infrastructure/SHARM_TO_GO_VPS.md's
# "Not covered here" section. Copy the output file off this VPS yourself
# until that decision is made.

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$repository_root/infrastructure/compose/sharm-to-go.compose.yaml"
env_file="${STG_ENV_FILE:-$repository_root/.env.sharm-to-go}"
backup_dir="${STG_BACKUP_DIR:-$repository_root/backups/sharm-to-go}"
retention_count="${STG_BACKUP_RETENTION:-14}"

if [[ ! -f "$env_file" ]]; then
  echo "Env file not found: $env_file (set STG_ENV_FILE, or create it — see infrastructure/SHARM_TO_GO_VPS.md)." >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a
source "$env_file"
set +a
postgres_db="${STG_POSTGRES_DB:-wego_sharm_to_go}"
postgres_user="${STG_POSTGRES_USER:-wego_app}"

mkdir -p "$backup_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output_file="$backup_dir/sharm-to-go-$timestamp.pgdump"

cleanup_partial() {
  local exit_code=$?
  if [[ $exit_code -ne 0 && -f "$output_file" ]]; then
    echo "Backup failed — removing partial file $output_file" >&2
    rm -f "$output_file"
  fi
  exit $exit_code
}
trap cleanup_partial EXIT

echo "Dumping $postgres_db from the running postgres service..."
docker compose --env-file "$env_file" -f "$compose_file" exec -T postgres \
  pg_dump -U "$postgres_user" -d "$postgres_db" --format=custom --no-owner --no-privileges \
  > "$output_file"

dump_size="$(stat -c%s "$output_file" 2>/dev/null || stat -f%z "$output_file")"
if [[ "$dump_size" -lt 100 ]]; then
  echo "Dump looks empty ($dump_size bytes) — refusing to count this as a real backup." >&2
  exit 1
fi

echo "Backup written: $output_file ($dump_size bytes)"

# Prune: keep only the most recent $retention_count backups.
mapfile -t existing < <(ls -1t "$backup_dir"/sharm-to-go-*.pgdump 2>/dev/null)
if (( ${#existing[@]} > retention_count )); then
  for old in "${existing[@]:$retention_count}"; do
    echo "Pruning old backup beyond retention ($retention_count): $old"
    rm -f "$old"
  done
fi
