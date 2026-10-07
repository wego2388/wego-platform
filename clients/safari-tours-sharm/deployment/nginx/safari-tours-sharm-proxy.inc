# Safari Tours Sharm — shared proxy settings for the gateway vhost.
# Install as: /etc/nginx/snippets/safari-tours-sharm-proxy.inc
# (path is referenced by safaritourssharm.com.conf; keep it Safari-only).
#
# The gateway is the ONLY trusted hop that writes forwarding headers:
#   - X-Forwarded-For  is OVERWRITTEN with the TCP peer ($remote_addr), never
#     appended to whatever the visitor sent ($proxy_add_x_forwarded_for would
#     let a visitor choose their own rate-limit bucket at the Safari edge).
#   - X-Forwarded-Proto is the literal "https": these server blocks only
#     exist on the TLS listener, and the visitor's own header is discarded.
# The Safari edge trusts these two headers only from loopback/private peers
# (infrastructure/nginx/nginx.conf: set_real_ip_from + $wego_forwarded_proto).

proxy_pass http://safari_tours_sharm_edge;
proxy_http_version 1.1;
proxy_set_header Connection "";
proxy_set_header Host $host;
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-Proto https;
proxy_set_header X-Forwarded-Host $host;
# Never forward RFC 7239 "Forwarded" from the visitor.
proxy_set_header Forwarded "";
proxy_set_header X-Forwarded-Port 443;
# Headers some frameworks honour; never let a visitor supply them.
proxy_set_header X-Forwarded-Prefix "";
proxy_set_header X-Forwarded-Ssl "";

proxy_connect_timeout 5s;
proxy_send_timeout 60s;
proxy_read_timeout 60s;
# The edge already sends HSTS/CSP/X-Frame-Options etc.; do not add a second
# copy here. Uploads are buffered by the gateway before reaching the edge.
proxy_request_buffering on;
proxy_intercept_errors off;
