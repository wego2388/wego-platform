# Sharm Divers Club's customer-facing Nuxt application.
FROM public.ecr.aws/docker/library/node:24-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43 AS build

WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@10.34.4 --activate
COPY . .
RUN --mount=type=cache,id=wego-pnpm-store,target=/pnpm/store \
    cd web \
    && pnpm config set store-dir /pnpm/store \
    && pnpm install --frozen-lockfile \
    && pnpm --filter @wego/sharm-divers-club-site build

FROM public.ecr.aws/docker/library/node:24-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43 AS runtime

RUN addgroup -S -g 10001 wego \
    && adduser -S -D -H -u 10001 -G wego wego
WORKDIR /app
COPY --from=build --chown=wego:wego \
    /workspace/web/apps/sharm-divers-club-site/.output /app/.output

USER 10001:10001
ENV HOST=0.0.0.0
ENV PORT=3000
EXPOSE 3000
ENTRYPOINT ["node", "/app/.output/server/index.mjs"]
