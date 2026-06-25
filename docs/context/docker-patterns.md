# Docker patterns

## Multi-stage Dockerfile
```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests -pl <service> -am

FROM eclipse-temurin:25-jre
RUN addgroup --system app && adduser --system --ingroup app app
USER app
WORKDIR /app
COPY --from=build /app/<service>/target/<service>.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Rules
- Replace `<service>` and `<service>.jar` with actual module/artifact — never `*.jar` glob
- Always non-root USER
- HEALTHCHECK mandatory — points to `/actuator/health`
- Image tag: `<service>:<git-sha>` — never `:latest` in K8s manifests
