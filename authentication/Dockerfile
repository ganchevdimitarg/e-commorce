# syntax=docker/dockerfile:1
# Build context is the repo root: docker build -f authentication/Dockerfile -t authentication:<git-sha> .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY authentication/pom.xml authentication/pom.xml
COPY authentication/src authentication/src
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -q -f authentication/pom.xml clean package -DskipTests \
    && cp authentication/target/authentication-0.0.1-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD curl -fsS http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
