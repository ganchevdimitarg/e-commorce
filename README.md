# E-Commerce Microservices Platform

A cloud-native e-commerce application built with a microservices architecture on
**Java 25** and **Spring Boot 4.1.0** / **Spring Cloud 2025.1.1**. Services are
independently deployable, own their data, and communicate through synchronous REST
(via the API Gateway) and asynchronous Kafka events.

## Architecture Overview

The platform is a Maven multi-module monorepo. The `gateway` is a reactive **WebFlux**
edge that owns authentication and rate limiting; downstream business services are
**WebMVC** and trust the `X-User-Id` / `X-User-Roles` headers propagated by the gateway.
Each service owns its own schema — there are no cross-service database joins and no
shared datasources.

### Business Services

- **authentication** – User authentication and authorization (OAuth2 / JWT, password reset)
- **catalog** – Product catalog, inventory, and product information
- **order** – Customer order processing and management
- **payment** – Payment processing and transactions (Stripe)
- **profile** – User profiles and account information (MongoDB)
- **notification** – User notifications (email, etc.)

### Infrastructure Services

- **config-server** – Centralized Spring Cloud configuration for all services
- **eureka-server** – Service discovery and registration (Netflix Eureka)
- **gateway** – Spring Cloud Gateway (WebFlux) — routing, auth, rate limiting
- **client** – Frontend / client interface

## Technology Stack

- **Language**: Java 25 (virtual threads, `ScopedValue`, `SequencedCollection`, records)
- **Framework**: Spring Boot 4.1.0, Spring Cloud 2025.1.1
- **Web**: Spring WebMVC (business services) · Spring WebFlux (`gateway`)
- **Service Discovery**: Netflix Eureka
- **API Gateway**: Spring Cloud Gateway
- **Configuration**: Spring Cloud Config
- **Relational DB**: PostgreSQL (per-service schemas) with Flyway migrations
- **Document DB**: MongoDB (`profile` service)
- **Cache / Locks**: Redis (Lettuce, Jackson JSON serialization, Redisson locks)
- **Messaging**: Apache Kafka (Zookeeper-based)
- **Resilience**: Resilience4j (circuit breaker, bulkhead, time limiter)
- **Secrets**: HashiCorp Vault
- **Tracing**: Zipkin
- **Errors**: RFC 9457 `application/problem+json`
- **Build Tool**: Maven (wrapper included)
- **Containerization**: Docker, Docker Compose

## Prerequisites

- JDK 25 or higher
- Docker and Docker Compose
- Maven is not required — use the bundled wrapper (`./mvnw` / `mvnw.cmd`)

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/ganchevdimitarg/e-commorce.git
cd e-commorce
```

### 2. Configure Environment

`docker-compose.yml` reads configuration from environment variables (database
credentials, Vault tokens, OAuth2 client IDs/secrets, mail and Stripe keys, etc.).
Provide these via a `.env` file in the project root before starting the stack.

### 3. Start Vault

Vault is used for secrets management and is started together with the infrastructure
stack. A helper script is also available:

```bash
./startVault.sh
```

### 4. Start the Infrastructure

`docker-compose.yml` provisions the backing infrastructure (not the microservices
themselves):

```bash
docker-compose up -d
```

This starts:

| Service     | Port(s)        | Purpose                                  |
|-------------|----------------|------------------------------------------|
| PostgreSQL  | 5432           | Relational store (per-service databases) |
| MongoDB     | 27017          | Document store for `profile`             |
| Redis       | 6379           | Cache and distributed locks              |
| Kafka       | 9092           | Event streaming                          |
| Zookeeper   | (internal)     | Kafka coordination                        |
| Zipkin      | 9411           | Distributed tracing                      |
| Vault       | 8200           | Secrets management                       |

### 5. Run the Services

> **Note:** the root Maven reactor cannot build the whole project at once while some
> modules are still being migrated to Boot 4. Build and run each module standalone.

Build a single module:

```bash
./mvnw -f <module>/pom.xml clean verify
```

Run a single module:

```bash
./mvnw -f <module>/pom.xml spring-boot:run
```

Start `config-server` and `eureka-server` first, then the `gateway`, then the business
services.

### 6. Access the Services

- **Eureka Dashboard**: http://localhost:8761
- **API Gateway**: http://localhost:8080
- **Zipkin**: http://localhost:9411
- **Vault UI**: http://localhost:8200

## Key Conventions

- **API versioning**: paths are `/api/v{n}/`, maintaining `n-1` compatibility.
- **Data ownership**: each service owns its schema; cross-service writes are propagated
  via Kafka events (topic naming `<domain>.<entity>.<event>`).
- **Migrations**: all schema changes are versioned Flyway migrations under
  `src/main/resources/db/migration/`; `ddl-auto` is `validate` only.
- **Resilience**: every outbound HTTP call is wrapped with Resilience4j circuit breaker,
  bulkhead, and time limiter.
- **Idempotency**: mutating cross-service endpoints support the `Idempotency-Key` header
  backed by Redis.
- **Errors**: all failures are returned as RFC 9457 `application/problem+json`.

## Database Migrations

Flyway owns all schema changes. Root Flyway configuration lives in `flyway.conf`.

```bash
./mvnw -f <module>/pom.xml flyway:validate   # check for migration drift
./mvnw -f <module>/pom.xml flyway:migrate     # apply migrations
```

## Testing

- **Unit**: JUnit 5 + AssertJ
- **Integration**: Testcontainers (real PostgreSQL / MongoDB / Redis / Kafka — never
  H2 or embedded Mongo)
- **Naming**: `should_<expectedBehavior>_when_<condition>`
- **Coverage gate**: 80% line, 100% on the domain model

Run tests for a module:

```bash
./mvnw -f <module>/pom.xml clean verify
```

## Project Structure

```
e-commorce/
├── authentication/     # Authentication & authorization service
├── catalog/            # Product catalog service
├── order/              # Order management service
├── payment/            # Payment processing service
├── profile/            # User profile service (MongoDB)
├── notification/       # Notification service
├── config-server/      # Centralized Spring Cloud configuration
├── eureka-server/      # Service discovery
├── gateway/            # API Gateway (WebFlux)
├── client/             # Frontend client
├── vault/              # Vault config and init scripts
├── mongo-init/         # MongoDB init scripts
├── postgresql-init/    # PostgreSQL init scripts
├── docs/               # Architecture and pattern documentation
├── docker-compose.yml  # Infrastructure orchestration
├── flyway.conf         # Flyway configuration
└── pom.xml             # Parent (aggregator) POM
```

## Security

- Stateless JWT validation happens at the `gateway`; downstream services read the
  `X-User-Id` / `X-User-Roles` headers.
- Each service declares its own `SecurityFilterChain` bean.
- Secrets are stored in HashiCorp Vault and injected via environment variables — never
  committed to code or configuration files.

## Documentation

Additional architecture and pattern documentation lives under `docs/` (see
`docs/context/`), and repository-wide conventions are captured in `CLAUDE.md`.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Troubleshooting

### Services Not Registering with Eureka

- Ensure `eureka-server` and `config-server` are running first
- Verify network connectivity between services
- Check the service configuration served by the Config Server

### Database Migration Failures

- Verify database connectivity and credentials
- Check the Flyway configuration in `flyway.conf`
- Never edit a committed migration — add a new versioned migration

### Docker Compose Issues

- Ensure the Docker daemon is running
- Check for port conflicts with `docker ps`
- Review logs with `docker-compose logs <service-name>`
- Confirm required environment variables are set (see `.env`)

## License

This project is open source and available under the [MIT License](LICENSE).

## Contact

For questions or support, please open an issue in the GitHub repository.

---

**Note**: This is a demonstration project showcasing microservices architecture patterns
and best practices for building scalable, cloud-native e-commerce applications.
