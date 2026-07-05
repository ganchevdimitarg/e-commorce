FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests -pl gateway -am

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN addgroup --system app && adduser --system --ingroup app app
USER app
WORKDIR /app
COPY --from=build /app/gateway/target/gateway-0.0.1-SNAPSHOT.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
