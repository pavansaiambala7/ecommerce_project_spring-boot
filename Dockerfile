# Multi-stage Dockerfile for the Spring Boot application.

# ---------------------------------------------------------------------------
# Stage 0: React storefront
#
# Built here rather than through Maven so the backend build stays free of a
# Node toolchain. package.json is copied on its own first so `npm ci` is only
# re-run when dependencies actually change, not on every source edit.
# ---------------------------------------------------------------------------
FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY frontend/package*.json ./
# ci, not install: it installs exactly what package-lock.json pins, so an image
# built today and one built next month contain the same dependency tree.
# The cache mount keeps npm's download cache between builds, so a rebuild
# that does not change package-lock.json installs from disk, not the network.
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
# vite.config.js writes to ../src/main/resources/static, which resolves to
# /src/main/resources/static from this WORKDIR - not /frontend/dist. That path
# is what the builder stage copies from below.
RUN npm run build

# ---------------------------------------------------------------------------
# Stage 1: build
# ---------------------------------------------------------------------------
# One stage, not a separate dependency stage: the Maven cache mount below keeps
# the repository between builds, which does the same job without re-downloading
# every dependency whenever pom.xml is touched.
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# The compiled SPA ships inside the jar as ordinary static resources, so the
# app is served from one origin and needs no CORS in production. A `mvn
# package` run outside Docker skips this and produces a backend-only jar.
COPY --from=frontend /src/main/resources/static ./src/main/resources/static
# Tests already ran in the pipeline's Test stage; running them again here
# would double the build for no extra signal.
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests -B -T 1C

# ---------------------------------------------------------------------------
# Stage 2: runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as an unprivileged user. The previous image ran the application as root,
# so a remote code execution bug would have started with full container
# privileges.
RUN groupadd --system --gid 1001 appuser \
 && useradd --system --uid 1001 --gid appuser --home /app appuser

COPY --from=builder --chown=appuser:appuser /app/target/*.jar app.jar

USER appuser

EXPOSE 8080

# Readiness, not merely "is the port open". The previous check accepted any
# HTTP status line, so a container whose database connection had failed still
# reported healthy - the one failure worth catching was the one it missed.
# /actuator/health/readiness reports DOWN when the datasource is unreachable.
#
# bash, not sh: /dev/tcp is a bash feature and this image's /bin/sh is dash,
# where the redirect silently fails and the container is marked unhealthy no
# matter how well the app is serving.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
