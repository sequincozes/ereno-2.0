# ERENO backend: the core + HTTP API (`serve`). Build context: api/
# The electrical-sources/ data (~560 MB) is not baked in; mount it at
# /app/electrical-sources (docker-compose.yml does).

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
# Dependencies first, so source edits reuse the cached layer.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
ARG GIT_COMMIT=unknown
ENV ERENO_GIT_COMMIT=${GIT_COMMIT} \
    ERENO_API_BIND=0.0.0.0 \
    JAVA_OPTS="" \
    ERENO_RUNNER_JVM_ARGS="" \
    ERENO_WORKERS=1
WORKDIR /app
# curl is only for the compose healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/target/ERENO-1.0-SNAPSHOT-uber.jar /app/ereno.jar
COPY config ./config
RUN mkdir -p /app/runs /app/target /app/electrical-sources
EXPOSE 8080
# Binding 0.0.0.0 makes the server require ERENO_API_TOKEN (it refuses to start otherwise).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dereno.api.workers=$ERENO_WORKERS -Dereno.runner.jvmArgs=\"$ERENO_RUNNER_JVM_ARGS\" -jar /app/ereno.jar serve"]
