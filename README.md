# E-Commerce Microservices Platform

A cloud-native e-commerce application built with a microservices architecture using Spring Boot, Spring Cloud, and containerization technologies.

## Architecture Overview

This project implements a distributed microservices architecture for an e-commerce platform, featuring independent, scalable services that communicate through well-defined APIs and message-driven communication.

### Microservices

- **Authentication Service** - Handles user authentication and authorization
- **Catalog Service** - Manages product catalog, inventory, and product information
- **Order Service** - Processes and manages customer orders
- **Payment Service** - Handles payment processing and transactions
- **Profile Service** - Manages user profiles and account information
- **Notification Service** - Sends notifications to users (email, SMS, etc.)

### Infrastructure Services

- **Eureka Server** - Service discovery and registration
- **Gateway** - API Gateway for routing and load balancing
- **Client** - Frontend application/client interface

### Monitoring & Observability

- **Prometheus** - Metrics collection and monitoring
- **Grafana** - Metrics visualization and dashboards

## Technology Stack

- **Framework**: Spring Boot, Spring Cloud
- **Service Discovery**: Netflix Eureka
- **API Gateway**: Spring Cloud Gateway
- **Configuration**: HashiCorp Vault
- **Containerization**: Docker, Docker Compose
- **Database Migration**: Flyway
- **Security**: HashiCorp Vault (for secrets management)
- **Monitoring**: Prometheus, Grafana
- **Build Tool**: Maven

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker and Docker Compose
- HashiCorp Vault (optional, for secrets management)

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/ganchevdimitarg/e-commorce.git
cd e-commorce
```

### 2. Start Vault (Optional)

If using HashiCorp Vault for secrets management:

```bash
./startVault.sh
```

### 3. Build the Project

```bash
./mvnw clean install
```

### 4. Start Services with Docker Compose

```bash
docker-compose up -d
```

This will start all microservices along with their dependencies (databases, message brokers, etc.).

### 5. Access the Services

- **Eureka Dashboard**: http://localhost:8761
- **API Gateway**: http://localhost:8080
- **Grafana Dashboard**: http://localhost:3000
- **Prometheus**: http://localhost:9090

## Service Architecture

### Service Communication

Services communicate through:
- **Synchronous**: REST APIs via the API Gateway
- **Asynchronous**: Message-driven communication (event bus)

### Configuration Management

Service configuration and secrets are sourced from HashiCorp Vault
(`spring.config.import: optional:vault://`), allowing for:
- Environment-specific configurations
- Secure credential management

### Service Discovery

Services register themselves with Eureka Server on startup, enabling:
- Dynamic service location
- Load balancing
- Health monitoring

## Database Management

### Flyway Migrations

Database migrations are managed using Flyway. Configuration is available in `flyway.conf`.

To run migrations manually:

```bash
mvn flyway:migrate
```

## Monitoring

### Prometheus

Metrics are collected from all microservices and exposed for Prometheus scraping.

### Grafana

Pre-configured dashboards are available for monitoring:
- Service health
- Request rates
- Response times
- Error rates
- Resource utilization

## Development

### Running Individual Services

Each microservice can be run independently:

```bash
cd <service-name>
mvn spring-boot:run
```

### Adding a New Service

1. Create a new module in the parent POM
2. Add service discovery configuration
3. Wire Vault-backed configuration (`bootstrap.yml`)
4. Add to docker-compose.yaml
5. Configure monitoring endpoints

## Project Structure

```
e-commorce/
├── authentication/       # Authentication & authorization service
├── catalog/             # Product catalog service
├── order/               # Order management service
├── payment/             # Payment processing service
├── profile/             # User profile service
├── notification/        # Notification service
├── eureka-server/       # Service discovery
├── gateway/             # API Gateway
├── client/              # Frontend client
├── prometheus/          # Prometheus configuration
├── grafana/             # Grafana dashboards
├── docker-compose.yaml  # Docker orchestration
├── flyway.conf          # Database migration config
└── pom.xml              # Parent POM
```

## Security

- Authentication is handled by the dedicated Authentication service
- Secrets are managed through HashiCorp Vault
- API Gateway handles request validation and routing
- Service-to-service communication can be secured with mutual TLS

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Best Practices Implemented

- **Single Responsibility**: Each service handles a specific business capability
- **Decentralized Data**: Each service manages its own database
- **Independent Deployment**: Services can be deployed independently
- **Fault Tolerance**: Circuit breakers and fallback mechanisms
- **Observability**: Comprehensive logging, metrics, and tracing
- **Configuration Management**: Externalized configuration
- **API Gateway Pattern**: Centralized entry point for all clients

## Troubleshooting

### Services Not Registering with Eureka

- Check if Eureka Server is running
- Verify network connectivity between services
- Check service configuration in Vault

### Database Migration Failures

- Verify database connectivity
- Check Flyway configuration in `flyway.conf`
- Ensure migrations are in the correct order

### Docker Compose Issues

- Ensure Docker daemon is running
- Check port conflicts with `docker ps`
- Review logs with `docker-compose logs <service-name>`

## License

This project is open source and available under the [MIT License](LICENSE).

## Contact

For questions or support, please open an issue in the GitHub repository.

---

**Note**: This is a demonstration project showcasing microservices architecture patterns and best practices for building scalable, cloud-native e-commerce applications.
