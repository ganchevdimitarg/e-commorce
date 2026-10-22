FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
 && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
RUN addgroup --system app && adduser --system --ingroup app app
USER app
WORKDIR /app
COPY --from=build /app/target/catalog-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8084 8088
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8088/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
