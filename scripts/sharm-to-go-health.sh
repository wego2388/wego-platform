#!/usr/bin/env bash
set -uo pipefail
# Deliberately not -e: every real endpoint must be checked even if an
# earlier one fails, so one down service doesn't hide another.

# Checks every real public surface of the deployed Sharm To Go stack —
# the same four endpoints infrastructure/SHARM_TO_GO_VPS.md's own "Verify"
# section curls by hand after a deploy, run here as a repeatable, scriptable
# check. Exits non-zero if anything is down, so this can be wired into cron
# or any alerting channel later. It sends no alert itself — there is no
# notification channel (Slack/email/etc.) configured for this project yet;
# see infrastructure/SHARM_TO_GO_VPS.md's "Not covered here" section.

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <site-domain> <admin-domain>" >&2
  echo "Example: $0 sharmtogo.com admin.sharmtogo.com" >&2
  exit 2
fi

site_domain="$1"
admin_domain="$2"
failures=0

check() {
  local description="$1"
  local url="$2"
  local expect="$3"
  local body
  if ! body="$(curl -fsS --max-time 10 "$url" 2>&1)"; then
    echo "FAIL  $description — could not reach $url" >&2
    failures=$((failures + 1))
    return
  fi
  if [[ -n "$expect" && "$body" != *"$expect"* ]]; then
    echo "FAIL  $description — $url responded but without expected content ($expect)" >&2
    failures=$((failures + 1))
    return
  fi
  echo "OK    $description"
}

check "Public site is reachable"      "https://$site_domain/robots.txt"                                  "Sitemap"
check "Public site sitemap is served" "https://$site_domain/sitemap.xml"                                 "<urlset"
check "Backend health, via the site"  "https://$site_domain/api/catalog/categories"                      ""
check "Staff ERP login page serves"   "https://$admin_domain/login"                                      ""

if [[ "$failures" -gt 0 ]]; then
  echo "$failures check(s) failed." >&2
  exit 1
fi
echo "All checks passed."
