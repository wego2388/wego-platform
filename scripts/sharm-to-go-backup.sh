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
#
# Encryption: opt-in via STG_BACKUP_PASSPHRASE. A database dump is customer
# PII (names, phone numbers, emails) sitting in a plain file on disk — if
# it's ever copied off this VPS (which the comment above says to do), an
# unencrypted copy is a real exposure. When the passphrase is set, the dump
# is symmetrically encrypted with GPG (AES256) and the plaintext file is
# removed; sharm-to-go-restore-drill.sh decrypts it automatically. Kept
# deliberately simple — a shared passphrase, not a recipient/PKI setup —
# because nothing here manages key distribution to multiple people yet.
# Store the passphrase somewhere durable and NOT on this VPS (a password
# manager); losing it makes every encrypted backup permanently unreadable.

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

if [[ -n "${STG_BACKUP_PASSPHRASE:-}" ]]; then
  encrypted_file="$output_file.gpg"
  echo "Encrypting backup (STG_BACKUP_PASSPHRASE is set)..."
  gpg --batch --yes --symmetric --cipher-algo AES256 \
    --passphrase-fd 0 --output "$encrypted_file" "$output_file" <<<"$STG_BACKUP_PASSPHRASE"
  rm -f "$output_file"
  output_file="$encrypted_file"
  echo "Backup encrypted: $output_file"
else
  echo "WARNING: STG_BACKUP_PASSPHRASE is not set — this backup is unencrypted plaintext. Set it to encrypt backups containing customer PII (names, phone numbers, emails)." >&2
fi

# Prune: keep only the most recent $retention_count backups, encrypted or
# not — a mixed backup_dir (some old plaintext, some new .gpg) is expected
# right after encryption is first turned on, and both count toward the
# same retention budget.
mapfile -t existing < <(ls -1t "$backup_dir"/sharm-to-go-*.pgdump "$backup_dir"/sharm-to-go-*.pgdump.gpg 2>/dev/null)
if (( ${#existing[@]} > retention_count )); then
  for old in "${existing[@]:$retention_count}"; do
    echo "Pruning old backup beyond retention ($retention_count): $old"
    rm -f "$old"
  done
fi
