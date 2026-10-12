# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# Stage 1 — build
#
# Dependencies are resolved from a pom-only layer so that editing source code
# reuses the cached dependency download instead of re-fetching the world.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Only the POM first: this layer only invalidates when dependencies change.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2 — runtime
#
# A JRE only, no build tooling, running as an unprivileged user.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is absent from the base image and the healthcheck needs it.
RUN apk add --no-cache curl tini \
    && addgroup -S -g 10001 shopwise \
    && adduser -S -u 10001 -G shopwise shopwise

WORKDIR /app

COPY --from=build --chown=shopwise:shopwise /build/target/*.jar /app/app.jar

USER shopwise
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE="prod"

# tini reaps zombies and forwards signals, so `docker stop` reaches the JVM and
# Spring Boot's graceful shutdown actually runs.
ENTRYPOINT ["/sbin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

# 127.0.0.1 rather than localhost: the name resolves to ::1 first inside a
# container, so an explicit address keeps the probe independent of that.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
