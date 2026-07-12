# Multi-stage build — see docs/context/docker-patterns.md.
# Built from the repo root (Docker context = monorepo root) so Maven can resolve the
# parent POM.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests -pl eureka-server -am

FROM eclipse-temurin:25-jre
# non-root runtime user; curl is required by the HEALTHCHECK below
RUN addgroup --system app \
    && adduser --system --ingroup app app \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
USER app
WORKDIR /app
COPY --from=build /app/eureka-server/target/eureka-server.jar app.jar
EXPOSE 8761
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
