#!/usr/bin/env bash
# Disable inherited or explicitly requested Bash tracing before handling secrets.
set +x
# Owner-operated Safari-only Gmail credential installation on the VPS.
# Run interactively as resortos. The app password is never an argument, env
# variable, shell-history entry or log line. This script does NOT enable mail.
set -euo pipefail

env_file=/srv/safari-tours-sharm/shared/.env.production

die() {
  printf 'Safari Gmail setup stopped: %s\n' "$1" >&2
  exit 1
}

[[ $(id -un) == resortos ]] || die 'run as the Safari deploy user resortos'
[[ -t 0 ]] || die 'an interactive terminal is required'
[[ -f "$env_file" && ! -L "$env_file" ]] || die 'Safari production env file missing or symlinked'
[[ $(stat -c %a "$env_file") == 600 ]] || die 'Safari production env file must be mode 600'
[[ $(stat -c %U "$env_file") == resortos ]] || die 'Safari production env file owner mismatch'
grep -qxF 'TOURS_OPERATOR_NOTIFICATIONS_ENABLED=false' "$env_file" ||
  die 'notification dispatcher must remain disabled during credential setup'

keys=(
  TOURS_OPERATOR_NOTIFICATIONS_ENABLED
  TOURS_OPERATOR_NOTIFICATIONS_FROM
  TOURS_OPERATOR_NOTIFICATIONS_REPLY_TO
  SPRING_MAIL_HOST
  SPRING_MAIL_PORT
  SPRING_MAIL_USERNAME
  SPRING_MAIL_PASSWORD
)
for key in "${keys[@]}"; do
  count=$(grep -c "^${key}=" "$env_file" || true)
  [[ "$count" == 1 ]] || die "expected one $key entry; found $count"
done

printf 'Account: safaritourssharm@gmail.com\n'
printf 'Paste the dedicated Google App Password; input stays hidden.\n'
IFS= read -r -s -p 'Safari VPS App Password: ' app_password
printf '\n'
app_password=${app_password// /}
[[ $app_password =~ ^[A-Za-z0-9]{16}$ ]] || die 'expected 16 letters/digits; no settings changed'

umask 077
temp_file=$(mktemp "${env_file}.tmp.XXXXXX")
cleanup() {
  unset app_password
  if [[ -n ${temp_file:-} && -e $temp_file ]]; then rm -f -- "$temp_file"; fi
}
trap cleanup EXIT

while IFS= read -r line || [[ -n $line ]]; do
  key=${line%%=*}
  case $key in
    TOURS_OPERATOR_NOTIFICATIONS_FROM|TOURS_OPERATOR_NOTIFICATIONS_REPLY_TO|SPRING_MAIL_USERNAME)
      printf '%s=safaritourssharm@gmail.com\n' "$key" ;;
    SPRING_MAIL_HOST) printf '%s=smtp.gmail.com\n' "$key" ;;
    SPRING_MAIL_PORT) printf '%s=587\n' "$key" ;;
    SPRING_MAIL_PASSWORD) printf '%s=%s\n' "$key" "$app_password" ;;
    *) printf '%s\n' "$line" ;;
  esac
done < "$env_file" > "$temp_file"

chmod 600 "$temp_file"
mv -f -- "$temp_file" "$env_file"
temp_file=
unset app_password
printf 'Safari SMTP settings saved. Customer email dispatch is still disabled.\n'
