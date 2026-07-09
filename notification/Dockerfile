# Multi-stage build — context is the monorepo root so Maven can resolve the parent POM and
# the internal `client` dependency via the reactor (-am).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests -pl notification -am

FROM eclipse-temurin:25-jre
# non-root runtime user; curl is required by the HEALTHCHECK below
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN addgroup --system app && adduser --system --ingroup app app
USER app
WORKDIR /app
COPY --from=build /app/notification/target/notification-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8085/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
