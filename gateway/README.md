## README.md

### E-commerce Gateway Service

**Description:**
The E-commerce Gateway service is a crucial component in the E-Commerce Microservices architecture. It acts as a reverse proxy, managing requests to different services and providing a unified interface for clients. The gateway supports various security measures such as OAuth2 authentication and circuit breaker patterns.

#### Key Features:

1. **OpenAPI 3 Documentation:**
    - **Swagger UI:** Provides interactive documentation of the E-commerce Gateway API.
    - **SpringDoc OpenAPI:** Generates static Swagger documentation, allowing developers to understand available endpoints and their functionalities without running the application.

2. **Security Configuration:**
    - **OAuth2 Client:** Supports OAuth2 authentication with Spring Security, providing secure access to the gateway service.
    - **Circuit Breaker:** Implements resilience patterns using Resilience4j for services that may fail or experience outages.

3. **Service Discovery and Routing:**
    - **Spring Cloud Gateway:** Manages routing of requests to different microservices using Eureka Client, providing load balancing and fault tolerance.
    - **Load Balancing:** Utilizes `lb://<service-name>` URIs for service discovery and load balancing.

4. **Environment Configuration:**
    - **Development Environment:** Uses `application-dev.yml` for configuration settings specific to the development environment.
    - **Vault Integration:** Supports Vault for dynamic configuration management, allowing secure access to sensitive information like client IDs and secrets.

5. **Logging and Monitoring:**
    - **Spring Boot Actuator:** Provides extensive health checks and metrics collection, aiding in monitoring and troubleshooting the service.
    - **Prometheus Integration:** Enables integration with Prometheus for monitoring and visualization of system performance.

#### Technical Requirements:

- **Java SDK Version:** JDK 21
- **Spring Boot Dependencies:**
    - Spring Cloud Gateway Server Web MVC
    - Resilience4j Reactor Circuit Breaker
    - OAuth2 Client
    - Eureka Client
    - Spring Boot Bootstrap
    - SpringDoc OpenAPI Starter WebMVC UI
    - Spring Cloud Vault Config

#### How to Run:

1. **Set Up Environment:**
    - Ensure all required services (e.g., Catalog Service, Profile Service) are running.
    - Configure environment variables or use a configuration management tool like Vault for sensitive data.

2. **Run the Gateway Service:**
    - Execute the following command from the project directory:
```shell script
mvn spring-boot:run
```


3. **Access Swagger UI:**
    - Open a web browser and navigate to `http://localhost:8081/swagger-ui.html` to access the API documentation.

#### Contributing:

- Fork the repository.
- Make your changes and submit a pull request.
- Ensure all new features are accompanied by tests and documented in the README.

---

**Note:** This README provides an overview of the E-commerce Gateway Service. For detailed instructions or specific sections, refer to the individual files within the `gateway` directory.