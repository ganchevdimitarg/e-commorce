# Authentication Service

OAuth 2.0 Authorization Server built with Spring Boot for securing microservices in an e-commerce platform.

## Overview

This service provides centralized authentication and authorization using OAuth 2.0 and OpenID Connect protocols. It issues JWT access tokens and manages client registrations with configurable scopes and grant types.

## Tech Stack

- **Java 25** with Lombok
- **Spring Boot 4.x** (Spring Security, OAuth2 Authorization Server)
- **Spring Data JPA** (PostgreSQL for client data)
- **Spring Data MongoDB** (User data)
- **Flyway** (Database migrations)
- **HashiCorp Vault** (Secret management)
- **Netflix Eureka** (Service discovery)

## Features

- 🔐 OAuth 2.0 Authorization Server
- 🎫 JWT token generation with RSA signing
- 👤 User authentication with MongoDB
- 📋 Client registration with PostgreSQL
- 🔄 Multiple grant types support (authorization_code, refresh_token, client_credentials)
- 🔑 Vault integration for secure configuration
- 📊 Actuator endpoints with Prometheus metrics

## Architecture

### Security Configuration
- **DefaultSecurityConfig**: Form login and endpoint protection
- **AuthorizationServerConfig**: OAuth2 endpoints with custom JWT claims
- **KeyManager**: RSA key pair generation for JWT signing

### Domain Model
- **AuthUser**: User entities stored in MongoDB with validation
- **Client**: OAuth2 client registrations with associated scopes, grant types, and token settings
- **Supporting entities**: GrantType, Scope, RedirectUri, TokenSetting

## Getting Started

### Prerequisites

- Java 25+
- Maven 3.8+
- PostgreSQL 13+
- MongoDB 5.0+
- HashiCorp Vault (optional, for production)
- Eureka Server running on port 8761

### Environment Variables

Create a `.env` file or configure in Vault:

```properties
MONGO_DB_USERNAME=your_mongo_user
MONGO_DB_PASSWORD=your_mongo_password
MONGO_DB_DATABASE=auth_db
POSTGRES_USER=your_postgres_user
POSTGRES_PASSWORD=your_postgres_password
```


### Database Setup

**PostgreSQL:**
```shell script
createdb registered_client
```


**MongoDB:**
```shell script
mongosh
use auth_db
```


Flyway migrations will automatically create tables on startup.

### Running the Service

```shell script
mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```


Service runs on **port 8082** by default.

## Configuration

### Token Settings
- Access Token TTL: 600 seconds (10 minutes)
- Refresh Token TTL: 7200 seconds (2 hours)

### Default Client
- **Client ID**: `gateway`
- **Client Secret**: `secret` (BCrypt encoded)
- **Grant Types**: authorization_code, refresh_token, client_credentials
- **Scopes**: catalog.read, catalog.write, profile.read, profile.write, order.read, order.write, notification.read

### Redirect URIs
- `http://127.0.0.1:8081/login/oauth2/code/gateway-client-oidc`
- `http://127.0.0.1:8081/authorized`

## API Endpoints

### OAuth2 Standard Endpoints
- **Authorization**: `GET /oauth2/authorize`
- **Token**: `POST /oauth2/token`
- **JWK Set**: `GET /oauth2/jwks`
- **OpenID Configuration**: `GET /.well-known/openid-configuration`

### Management Endpoints
- **Health**: `GET /actuator/health`
- **Metrics**: `GET /actuator/metrics`
- **Prometheus**: `GET /actuator/prometheus`

### API Documentation
- **Swagger UI**: `http://localhost:8082/swagger-ui.html`

## Testing

Run unit tests:
```shell script
mvn test
```


Test coverage includes:
- KeyManager RSA key generation
- ClientService client registration and retrieval
- UserService authentication

## Development

### Hot Reload
DevTools is enabled with LiveReload on port **35730**.

### Logging
Configure logging levels in `application-dev.yml`:
- Spring Security: TRACE
- OAuth2: INFO
- Vault: DEBUG

## Integration

### Eureka Registration
Service registers with Eureka at `http://localhost:8761/eureka`

### Vault Integration
Secrets are fetched from Vault at `http://localhost:8200` under path `secret/auth-service`

## Project Structure

```
authentication/
├── config/
│   ├── password/          # Password encoding
│   └── security/          # OAuth2 & security configs
├── dao/                   # Data access layer
├── domain/                # Entity models
├── service/               # Business logic
└── resources/
    ├── db/migration/      # Flyway SQL scripts
    └── *.yml              # Configuration files
```


## Contributing

1. Follow Spring Security best practices
2. Add unit tests for new features
3. Update Flyway migrations for schema changes
4. Document API changes in Swagger annotations

## License

Part of the e-commerce microservices platform.

---

**Note**: This service is intended for development. For production deployment, ensure proper secret management, TLS configuration, and token TTL adjustments.