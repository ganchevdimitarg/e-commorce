# AGENTS.md - Instructions for OpenCode

## Project Overview
- **Stack:** Java 21, Spring Boot 4.0, Maven
- **Architecture:** Distributed Microservices
- **Key Feature:** Virtual Threads enabled (`spring.threads.virtual.enabled=true`)

## Build & Test Commands
- **Build all:** `./mvnw clean install -DskipTests`
- **Run all tests:** `./mvnw test`
- **Run specific test:** `./mvnw test -Dtest={ClassName}`
- **Check formatting:** `./mvnw spotless:check` (if applicable)

## Coding Standards
- **Style:** Google Java Style Guide.
- **REST:** Use constructor injection for all Spring Beans.
- **API:** Always version endpoints (e.g., `/api/v1/...`).
- **Lombok:** Use `@RequiredArgsConstructor` for dependency injection.

## Operational Rules
- Always run the relevant test class after modifying code.
- If a new dependency is needed, add it to the parent `pom.xml` properties first.
- Adopt a professional, direct tone. Do not provide conversational filler.