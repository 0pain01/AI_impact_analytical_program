# syntax=docker/dockerfile:1
#
# Multi-stage build: compile with the full JDK/Maven toolchain, ship only the JRE + jar.
# Mirrors connector-gitlab's Dockerfile (ADR-0005) — see ADR-0007 for why connector-jenkins
# follows the same template. Build context must be the repo root's services/ dir (the module
# needs the reactor pom + platform-common), e.g.:
#   docker build -f services/connectors/connector-jenkins/Dockerfile -t connector-jenkins .
# infra/docker-compose.yml's connector-jenkins service sets this context/dockerfile pairing for you.

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Cache dependency resolution: copy just the POMs first so `mvn dependency:go-offline` layers
# survive source-only edits and don't get invalidated on every rebuild.
COPY pom.xml .
COPY platform-common/pom.xml platform-common/pom.xml
COPY api-core/pom.xml api-core/pom.xml
COPY ingestion-writer/pom.xml ingestion-writer/pom.xml
COPY metrics-engine/pom.xml metrics-engine/pom.xml
COPY identity-service/pom.xml identity-service/pom.xml
COPY connectors/connector-github/pom.xml connectors/connector-github/pom.xml
COPY connectors/connector-jira/pom.xml connectors/connector-jira/pom.xml
COPY connectors/connector-jenkins/pom.xml connectors/connector-jenkins/pom.xml
COPY connectors/connector-ai-telemetry/pom.xml connectors/connector-ai-telemetry/pom.xml
COPY connectors/connector-gitlab/pom.xml connectors/connector-gitlab/pom.xml

COPY platform-common/src platform-common/src
COPY connectors/connector-jenkins/src connectors/connector-jenkins/src

# platform-common is a plain (non-Boot) module the connector depends on directly — build+install
# it into the local repo, then package the connector, without touching the sibling modules
# above (their sources aren't even copied in, only their POMs, so the reactor can resolve them).
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl platform-common -am install -DskipTests \
    && mvn -q -pl connectors/connector-jenkins -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S aiimpacteval && adduser -S aiimpacteval -G aiimpacteval
WORKDIR /app
COPY --from=build /workspace/connectors/connector-jenkins/target/connector-jenkins-*.jar app.jar
USER aiimpacteval

EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
