#!/usr/bin/env bash
# Render the existing reviewed vhost for the ACTUAL containerized VPS gateway.
# Writes only stdout. Operator installs output atomically in the mounted dir.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
templates="$root/clients/safari-tours-sharm/deployment/nginx"
case "${1:-}" in
  bootstrap) template="$templates/safaritourssharm.com.acme-bootstrap.conf" ;;
  live) template="$templates/safaritourssharm.com.conf" ;;
  maintenance) template="$templates/safaritourssharm.com.maintenance.conf" ;;
  proxy)
    # DNS resolution happens per request, not during gateway startup. Safari
    # being absent/replaced must never prevent El Kheima nginx starting.
    printf '%s\n' 'resolver 127.0.0.11 valid=5s ipv6=off;' 'resolver_timeout 2s;' \
      'set $safari_upstream safari-edge:8080;'
    sed 's@proxy_pass http://safari_tours_sharm_edge;@proxy_pass http://$safari_upstream;@' \
      "$templates/safari-tours-sharm-proxy.inc"
    exit 0 ;;
  *) printf 'Usage: %s bootstrap|live|maintenance|proxy\n' "$0" >&2; exit 64 ;;
esac
# Queries/referrers/user-agent are deliberately excluded. Nginx upstream-error
# messages (even critical temp-file errors) include the full request: disable
# request error logs on Safari ONLY; main nginx startup errors stay on stderr.
# status, path and request ID remain available in the privacy-safe access log.
printf '%s\n' 'log_format safari_private escape=json '\
  "  '{\"host\":\"\$host\",\"method\":\"\$request_method\",\"path\":\"\$uri\",\"status\":\"\$status\",\"request_id\":\"\$request_id\"}';"
sed \
  -e '/^upstream safari_tours_sharm_edge {/,/^}/d' \
  -e '/^[[:space:]]*access_log /d' \
  -e '/^[[:space:]]*error_log /d' \
  -e '/^server {/a\    access_log /dev/stdout safari_private;\n    error_log /dev/null crit;\n    server_tokens off;\n    ssl_protocols TLSv1.2 TLSv1.3;\n    ssl_session_tickets off;' \
  -e 's@/var/www/safari-tours-sharm-acme@/var/www/certbot@g' \
  -e 's@/etc/nginx/snippets/safari-tours-sharm-proxy.inc@/etc/nginx/vps-sites/proxy.inc@g' \
  -e 's@/srv/safari-tours-sharm/shared/maintenance@/etc/nginx/vps-sites/maintenance@g' \
  "$template"
