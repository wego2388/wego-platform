# One Dockerfile for both Sharm To Go web apps — pick with
# `--build-arg WEB_APP=sharm-to-go-site` or `sharm-to-go-erp`. Same node pin
# and frozen-lockfile discipline as web.Dockerfile.
FROM public.ecr.aws/docker/library/node:24-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43 AS build

ARG WEB_APP
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@10.34.4 --activate
COPY . .
RUN test -n "$WEB_APP" && cd web && pnpm install --frozen-lockfile && pnpm --filter "@wego/$WEB_APP" build

FROM public.ecr.aws/docker/library/node:24-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43 AS runtime

ARG WEB_APP
RUN addgroup -S -g 10001 wego \
    && adduser -S -D -H -u 10001 -G wego wego
WORKDIR /app
COPY --from=build --chown=wego:wego /workspace/web/apps/${WEB_APP}/.output /app/.output

USER 10001:10001
ENV HOST=0.0.0.0
ENV PORT=3000
EXPOSE 3000
ENTRYPOINT ["node", "/app/.output/server/index.mjs"]
