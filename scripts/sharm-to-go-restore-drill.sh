#!/usr/bin/env bash
set -euo pipefail

# Rehearses restoring a Sharm To Go backup (produced by
# scripts/sharm-to-go-backup.sh) into a throwaway, isolated Postgres
# container — never the live database. A backup nobody has ever restored
# is not a real backup; this is the drill that proves the file is actually
# restorable, not just that pg_dump exited 0.
#
# Accepts either a plain .pgdump or a .pgdump.gpg produced when
# STG_BACKUP_PASSPHRASE was set for the backup run — in that case the same
# passphrase must be in STG_BACKUP_PASSPHRASE here too, to decrypt it into
# a temp file first (removed again at the end, so a decrypted plaintext
# copy never outlives this drill).

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <path-to-backup.pgdump|path-to-backup.pgdump.gpg>" >&2
  exit 1
fi

backup_file="$1"
if [[ ! -f "$backup_file" ]]; then
  echo "Backup file not found: $backup_file" >&2
  exit 1
fi

container_name="sharm-to-go-restore-drill-$$"
drill_db="restore_drill"
drill_user="restore_drill"
drill_password="restore-drill-$$"
postgres_image="public.ecr.aws/docker/library/postgres:18.4-alpine"
decrypted_file=""

cleanup() {
  docker rm -f "$container_name" >/dev/null 2>&1 || true
  if [[ -n "$decrypted_file" && -f "$decrypted_file" ]]; then
    rm -f "$decrypted_file"
  fi
}
trap cleanup EXIT

if [[ "$backup_file" == *.gpg ]]; then
  if [[ -z "${STG_BACKUP_PASSPHRASE:-}" ]]; then
    echo "$backup_file is encrypted — set STG_BACKUP_PASSPHRASE to decrypt it for this drill." >&2
    exit 1
  fi
  decrypted_file="$(mktemp "${TMPDIR:-/tmp}/sharm-to-go-restore-drill-XXXXXX.pgdump")"
  echo "Decrypting $backup_file..."
  gpg --batch --yes --decrypt --passphrase-fd 0 --output "$decrypted_file" "$backup_file" <<<"$STG_BACKUP_PASSPHRASE"
  backup_file="$decrypted_file"
fi

echo "Starting a throwaway Postgres container for the drill ($container_name)..."
docker run -d --name "$container_name" \
  -e POSTGRES_DB="$drill_db" -e POSTGRES_USER="$drill_user" -e POSTGRES_PASSWORD="$drill_password" \
  "$postgres_image" >/dev/null

echo "Waiting for it to accept connections..."
ready=0
for _ in $(seq 1 30); do
  if docker exec "$container_name" pg_isready -U "$drill_user" -d "$drill_db" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
if [[ "$ready" -ne 1 ]]; then
  echo "Throwaway Postgres never became ready." >&2
  exit 1
fi

echo "Restoring $backup_file into the throwaway container..."
docker exec -i "$container_name" pg_restore -U "$drill_user" -d "$drill_db" --no-owner --no-privileges < "$backup_file"

table_count="$(docker exec "$container_name" psql -U "$drill_user" -d "$drill_db" -tAc \
  "select count(*) from information_schema.tables where table_schema = 'wego'")"

if [[ "${table_count:-0}" -lt 1 ]]; then
  echo "Restore completed but the wego schema has no tables — something is wrong." >&2
  exit 1
fi

echo "Restore drill succeeded: $table_count table(s) restored into schema 'wego'."
