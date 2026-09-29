# Safari Tours Sharm's executable backend contains only platform kernels and
# the tours-operator product. It must never fall back to the Divers application.
FROM public.ecr.aws/docker/library/gradle:9.5.0-jdk25@sha256:03305b464e024b29cfaad1c4a41fed61d06d15453176d2180f65bd4358b789a6 AS build

WORKDIR /workspace
RUN chown gradle:gradle /workspace
COPY --chown=gradle:gradle . .
USER gradle
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    gradle --no-daemon :platform:apps:safari-tours-sharm:bootJar

FROM public.ecr.aws/docker/library/eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0 AS runtime

RUN addgroup -S -g 10001 wego \
    && adduser -S -D -H -u 10001 -G wego wego
WORKDIR /app
COPY --from=build --chown=wego:wego \
    /workspace/platform/apps/safari-tours-sharm/build/libs/safari-tours-sharm-0.1.0-SNAPSHOT.jar \
    /app/application.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
