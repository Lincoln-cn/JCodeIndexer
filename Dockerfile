# Multi-stage Dockerfile for Java Code Indexer
#
# 构建方式:
#   1. 本地构建: mvn package -q -DskipTests && docker build -t java-code-indexer .
#   2. CI 构建: 由 .github/workflows/release.yml 自动处理

FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="Java Code Indexer"
LABEL org.opencontainers.image.description="MCP-based Java code indexing server for AI coding assistants"
LABEL org.opencontainers.image.source="https://github.com/sodlinken/java-code-indexer"

RUN addgroup -S jindexer && adduser -S jindexer -G jindexer

# maven-shade-plugin 3.5+ replaces original jar (no -shaded suffix)
COPY target/java-code-indexer-*.jar /app/jindexer.jar

USER jindexer

ENTRYPOINT ["java", "-jar", "/app/jindexer.jar"]
