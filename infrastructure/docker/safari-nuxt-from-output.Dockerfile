# Low-disk, off-VPS fallback: first build the selected app from a verified
# source tree, then pass ONLY its fresh .output through --build-context output.
# SOURCE_SHA and OUTPUT_SHA256 are recorded in the release evidence; never
# reuse an unverified preview image or include the workspace node_modules.
# Host builds include glibc sharp binaries: use the verified amd64 Debian
# runtime, NOT Alpine/musl. Standard source Dockerfiles still use Alpine builds.
# Materialize .output symlinks into a separate context before hashing/copying.
FROM public.ecr.aws/docker/library/node:24-bookworm-slim@sha256:51b1100cc2a83d370c6a60952e3f2989c8a43159d0e38586e090f3b3326efefd
ARG SOURCE_SHA
ARG OUTPUT_SHA256
ARG APP_PORT
RUN echo "$SOURCE_SHA" | grep -Eq '^[0-9a-f]{40}$' \
    && echo "$OUTPUT_SHA256" | grep -Eq '^[0-9a-f]{64}$' \
    && { [ "$APP_PORT" = 3000 ] || [ "$APP_PORT" = 3001 ]; } \
    && groupadd --gid 10001 wego \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin wego
WORKDIR /app/.output
COPY --from=output --chown=wego:wego . .
RUN test -f server/index.mjs \
    && test -z "$(find . -type l -print -quit)" \
    && actual="$(find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | cut -d' ' -f1)" \
    && test "$actual" = "$OUTPUT_SHA256"
LABEL org.opencontainers.image.revision=$SOURCE_SHA \
      org.opencontainers.image.source="safari-tours-sharm" \
      wego.output.sha256=$OUTPUT_SHA256
USER 10001:10001
ENV HOST=0.0.0.0 PORT=$APP_PORT
ENTRYPOINT ["node", "/app/.output/server/index.mjs"]
