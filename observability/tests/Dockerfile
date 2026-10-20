# Separate test image: diagnostic fixtures never enter the production backend JAR.
FROM eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:107215954359f1ab0be058a247d7c64ed6e69c71c1682d471d38ef751ec8e964 AS build
WORKDIR /workspace
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
COPY backend/src/ src/
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress -DskipTests test-compile dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/observability-dependencies

FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:35501d3f93173b420ac9001658eb68157e813316b865087e345c187fca422c4c
WORKDIR /app
COPY --from=build /workspace/target/classes/ classes/
COPY --from=build /workspace/target/test-classes/ test-classes/
COPY --from=build /workspace/target/observability-dependencies/ dependencies/
USER 10001:10001
ENTRYPOINT ["java", "-cp", "/app/classes:/app/test-classes:/app/dependencies/*", "com.finfast.common.observability.ObservabilityStackProbe", "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"]
