# Safari Tours Sharm backend image built from a pre-compiled jar.
#
# Use this Dockerfile when the ordinary source build (safari-backend.Dockerfile)
# cannot run inside Docker due to network restrictions that prevent Gradle plugin
# resolution (e.g. DNS isolation on the CI host for plugins-artifacts.gradle.org).
#
# Prerequisites:
#   • The jar must already be compiled locally:
#       JAVA_HOME=/home/wego/.jdks/temurin-25.0.3+9 \
#       ./gradlew :platform:apps:safari-tours-sharm:bootJar
#   • SHA256 of the jar must match the value passed as JAR_SHA256 build-arg.
#
# Build:
#   JAR=platform/apps/safari-tours-sharm/build/libs/safari-tours-sharm-0.1.0-SNAPSHOT.jar
#   SHA=$(sha256sum "$JAR" | cut -d' ' -f1)
#   docker build \
#     --build-arg JAR_SHA256="$SHA" \
#     -f infrastructure/docker/safari-backend-from-jar.Dockerfile \
#     -t wego-safari-media-backend:local \
#     --build-context jar=platform/apps/safari-tours-sharm/build/libs \
#     .
#
# This image is functionally identical to the one produced by safari-backend.Dockerfile
# — same base image, same UID, same entrypoint, same /data/safari-media directory.
# The runtime stage is pinned to the same digest.

FROM public.ecr.aws/docker/library/eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0 AS verify

ARG JAR_SHA256
RUN test -n "$JAR_SHA256" || { echo "JAR_SHA256 build-arg is required"; exit 1; }

COPY --from=jar safari-tours-sharm-0.1.0-SNAPSHOT.jar /tmp/application.jar

RUN actual="$(sha256sum /tmp/application.jar | cut -d' ' -f1)"; \
    if [ "$actual" != "$JAR_SHA256" ]; then \
      printf 'SHA256 mismatch\n  expected: %s\n  actual:   %s\n' "$JAR_SHA256" "$actual" >&2; \
      exit 1; \
    fi; \
    printf 'SHA256 verified: %s\n' "$actual"

FROM public.ecr.aws/docker/library/eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0 AS runtime

RUN addgroup -S -g 10001 wego \
    && adduser -S -D -H -u 10001 -G wego wego \
    && mkdir -p /data/safari-media \
    && chown 10001:10001 /data/safari-media \
    && chmod 700 /data/safari-media

WORKDIR /app
COPY --from=verify --chown=wego:wego /tmp/application.jar /app/application.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
