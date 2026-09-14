# Multi-stage Dockerfile for the Spring Boot application.

# ---------------------------------------------------------------------------
# Stage 1: dependencies (cached independently of source changes)
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

# ---------------------------------------------------------------------------
# Stage 2: build
# ---------------------------------------------------------------------------
FROM deps AS builder
WORKDIR /app
COPY src ./src
RUN mvn package -DskipTests -B

# ---------------------------------------------------------------------------
# Stage 3: runtime
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

# bash, not sh: /dev/tcp is a bash feature and this image's /bin/sh is dash,
# where the redirect silently fails and the container is marked unhealthy no
# matter how well the app is serving. Any HTTP status line counts as alive -
# /login answers 302, so grepping for a 200 here would be wrong too.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /login HTTP/1.0\\r\\n\\r\\n' >&3 && head -n 1 <&3 | grep -q HTTP"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
