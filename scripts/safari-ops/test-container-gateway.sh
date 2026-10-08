#!/usr/bin/env bash
# Disposable test: no production names, ports, networks, env or volumes used.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fixtures="$root/clients/safari-tours-sharm/deployment/gateway/test-fixtures"
image=public.ecr.aws/docker/library/nginx:1.30.4-alpine@sha256:97d490c12ba55b4946b01546d1c3ed324e8d41ab1c9fcb2a616aa470620e5b46
temp="$(mktemp -d /tmp/safari-gateway-test.XXXXXX)"
network="safari-gateway-test-$$"
gateway="$network-gateway"; edge="$network-edge"; holder="$network-holder"
cleanup() {
  docker rm -f "$gateway" "$edge" "$holder" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  # Exact mktemp-created directory owned solely by this test.
  rm -rf -- "$temp"
}
trap cleanup EXIT
mkdir -p "$temp/gateway/maintenance" "$temp/certs/live/safaritourssharm.com"
mkdir -p "$temp/unwritable-body"
chmod 0755 "$temp/unwritable-body"
docker run --rm --entrypoint sh -v "$temp/unwritable-body:/body" "$image" \
  -c 'chown 101:101 /body' >/dev/null
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -keyout "$temp/certs/live/safaritourssharm.com/privkey.pem" \
  -out "$temp/certs/live/safaritourssharm.com/fullchain.pem" \
  -subj /CN=safaritourssharm.com \
  -addext 'subjectAltName=DNS:safaritourssharm.com,DNS:www.safaritourssharm.com,DNS:staff.safaritourssharm.com' \
  >/dev/null 2>&1
bash "$root/scripts/safari-ops/render-container-gateway.sh" proxy > "$temp/gateway/proxy.inc"
bash "$root/scripts/safari-ops/render-container-gateway.sh" live > "$temp/gateway/safari.conf"
cp "$root/clients/safari-tours-sharm/deployment/nginx/maintenance/index.html" "$temp/gateway/maintenance/index.html"
docker network create "$network" >/dev/null
docker run -d --name "$gateway" --network "$network" --read-only \
  --tmpfs /var/cache/nginx --tmpfs /run \
  -p 127.0.0.1::80 -p 127.0.0.1::443 \
  -v "$fixtures/resort.conf:/etc/nginx/conf.d/default.conf:ro" \
  -v "$root/clients/safari-tours-sharm/deployment/gateway/gateway-loader.conf:/etc/nginx/conf.d/safari-loader.conf:ro" \
  -v "$temp/gateway:/etc/nginx/vps-sites:ro" -v "$temp/certs:/etc/letsencrypt:ro" \
  -v "$temp/unwritable-body:/unwritable-safari-body:ro" \
  "$image" >/dev/null
http_port="$(docker port "$gateway" 80/tcp | cut -d: -f2)"
https_port="$(docker port "$gateway" 443/tcp | cut -d: -f2)"
request() {
  curl --silent --show-error --max-time 8 \
    --cacert "$temp/certs/live/safaritourssharm.com/fullchain.pem" \
    --resolve "safaritourssharm.com:$https_port:127.0.0.1" \
    "https://safaritourssharm.com:$https_port$1" "${@:2}"
}
# Missing Safari must not prevent the real gateway starting or Resort routing.
docker exec "$gateway" nginx -t
# docker run -d and nginx -t prove process creation/config, not that the
# original master has finished starting workers. Fast CI hit a connection
# reset here. Bound startup readiness; retain exact response assertions.
ready=false
for attempt in $(seq 1 15); do
  [[ "$(docker inspect "$gateway" --format '{{.State.Running}}')" == true ]] \
    || { echo 'FAIL: gateway exited during startup' >&2; exit 1; }
  if curl -fsS --max-time 5 -H 'Host: elkheima.example.test' \
    "http://127.0.0.1:$http_port/" 2>/dev/null | grep -qx resort-unchanged; then
    ready=true
    break
  fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'FAIL: gateway did not become ready' >&2; exit 1; }
[[ "$(request '/absent?hmac=GW_PRIVACY_SENTINEL' -H 'Referer: https://example.test/?token=GW_PRIVACY_SENTINEL' -o /dev/null -w '%{http_code}')" == 502 ]]
start_edge() {
  docker run -d --name "$edge" --network "$network" --network-alias safari-edge \
    -v "$fixtures/echo.conf:/etc/nginx/conf.d/default.conf:ro" "$image" >/dev/null
}
start_edge
first_ip="$(docker inspect "$edge" --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')"
for attempt in $(seq 1 15); do
  code="$(request '/success?hmac=GW_PRIVACY_SENTINEL' -o "$temp/response" -w '%{http_code}')"
  [[ "$code" == 200 ]] && break
  sleep 1
done
[[ "$code" == 200 ]]
request '/headers?hmac=GW_PRIVACY_SENTINEL' \
  -H 'X-Forwarded-For: 203.0.113.77' -H 'X-Forwarded-Proto: http' \
  -H 'Forwarded: for=spoof;proto=http' -H 'X-Forwarded-Prefix: /spoof' > "$temp/headers"
jq -e '.host == "safaritourssharm.com" and .proto == "https" and .xff != "203.0.113.77" and .forwarded == "" and .xfprefix == ""' "$temp/headers" >/dev/null
# Reserve the old edge address so replacement MUST obtain a different IP.
docker rm -f "$edge" >/dev/null
docker run -d --name "$holder" --network "$network" --ip "$first_ip" "$image" >/dev/null
start_edge
second_ip="$(docker inspect "$edge" --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')"
[[ "$first_ip" != "$second_ip" ]]
for attempt in $(seq 1 15); do
  code="$(request /replacement -o /dev/null -w '%{http_code}')"
  [[ "$code" == 200 ]] && break
  sleep 1
done
[[ "$code" == 200 ]]
curl -fsS --max-time 5 -H 'Host: elkheima.example.test' "http://127.0.0.1:$http_port/" | grep -qx resort-unchanged
# Critical error regression: no writable body temp directory on read-only
# container. Force buffering before proxying; failure must not log credentials.
sed -i '/client_max_body_size 1m;/a\    client_body_buffer_size 1k;\n    client_body_temp_path /unwritable-safari-body;' "$temp/gateway/safari.conf"
docker exec "$gateway" nginx -t
docker exec "$gateway" nginx -s reload
for attempt in $(seq 1 10); do
  code="$(head -c 131072 /dev/zero | request '/temp-failure?hmac=GW_PRIVACY_SENTINEL' \
    -H 'Referer: https://example.test/?token=GW_PRIVACY_SENTINEL' \
    --data-binary @- -o /dev/null -w '%{http_code}')"
  [[ "$code" == 500 ]] && break
  sleep 1
done
[[ "$code" == 500 ]] || { echo "FAIL: critical-error fixture returned $code, expected 500" >&2; exit 1; }
if docker logs "$gateway" 2>&1 | grep -q GW_PRIVACY_SENTINEL; then
  echo 'FAIL: sensitive query/referrer reached gateway logs' >&2; exit 1
fi
# Syntax of all deployment variants under the real RO/directory-mount layout.
for mode in bootstrap maintenance live; do
  bash "$root/scripts/safari-ops/render-container-gateway.sh" "$mode" > "$temp/gateway/safari.conf"
  docker exec "$gateway" nginx -t
done
echo 'PASS: absent Safari, Resort continuity, dynamic IP recovery without reload, spoofed headers, critical-error privacy and 3 variants'
