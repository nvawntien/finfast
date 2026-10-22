# syntax=docker/dockerfile:1
FROM eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:107215954359f1ab0be058a247d7c64ed6e69c71c1682d471d38ef751ec8e964 AS build

WORKDIR /workspace

# 1. Cache Maven Wrapper and project descriptor
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# 2. Package application skipping integration tests (run in CI pipeline)
COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress -DexcludedGroups=integration package

# -----------------------------------------------------------------------------
# Runtime stage: Minimal hardened non-root JRE 21 environment
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:35501d3f93173b420ac9001658eb68157e813316b865087e345c187fca422c4c AS runtime

# Create dedicated unprivileged system user/group
RUN groupadd --gid 10001 finfast \
    && useradd --uid 10001 --gid finfast --no-create-home --shell /usr/sbin/nologin finfast

WORKDIR /app

# Copy executable JAR with unprivileged ownership
COPY --from=build --chown=finfast:finfast /workspace/target/finfast-backend.jar /app/app.jar

# Container-optimized JVM memory management
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080

# Production container healthcheck
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
