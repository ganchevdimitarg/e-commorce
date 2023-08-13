# E-Commerce Project

Welcome to the E-Commerce project! This project aims to provide a robust and scalable e-commerce solution. It includes a variety of features to support online shopping, such as product management, cart functionality, user authentication, and more.

## Table of Contents

- [Features](#features)
- [Technologies Used](#technologies-used)
- [Installation](#installation)
- [Usage](#usage)
- [Contributing](#contributing)
- [License](#license)

## Features

This e-commerce project includes the following features:

- User authentication: Users can sign up, log in, and manage their accounts.
- Product management: Admins can add, edit, and delete products.
- Product listing: Users can browse and search for products.
- Shopping cart: Users can add products to their cart and manage the contents.
- Order management: Admins can view and manage customer orders.
- Payment integration: Integration with popular payment gateways to process payments.
- User reviews and ratings: Users can leave reviews and ratings for products.
- Category management: Admins can categorize products for easier navigation.
- Wishlist: Users can add products to their wishlist for future reference.
- Order tracking: Users can track the status of their orders.
- Inventory management: Admins can track product availability and stock levels.

## Technologies Used

The E-Commerce project is built using the following technologies:

- Frontend:

- Backend:
  - Spring Boot
  - Spring Data
  - Spring Security
  - Spring Vault
  - Spring Gateway
  - OAuth 2.0 Resource Server
  - OAuth 2.0 Client
  - Spring WebFlux
  - Flyway
  - Zipkin
  - Docker
  - Kafka
  
  - Database: 
    - MongoDB
    - Postgres

- Authentication:
  - Spring Authorization Server
    - JSON Web Tokens (JWT)
    - Opaque token

- Payment Integration:
  - Stripe

## Installation

To get started with the E-Commerce project, follow these steps:

1. Clone the repository:

   ```bash
   git clone https://github.com/ganchevdimitarg/e-commerce.git
   ```

2. Navigate to the project directory:

   ```bash
   cd e-commerce
   ```

3. Initiate the containers for Spring Cloud Vault:

   ```bash
   docker compose up -d vault vault-agent
   ```

4. Execute these two lines from the startVault.sh script.:

   ```bash
   docker exec vault /bin/sh -c "source /helpers/init.sh"
	 docker restart vault-agent
   ```

5. Start the following containers:
   -  MySQL,
   -  MongoDB,
   -  PostgreSQL
   -  Zipkin
   -  Prometheus
   -  Grafana
   -  Loki
   -  Promtail
   -  Node-exporter
   -  Cadvisor
   -  Zookeeper
   -  Kafka

   ```bash
   docker compose up -d db mongodb postgres zipkin prometheus grafana loki promtail node-exporter cadvisor zookeeper kafka
   ```

7. Initiate the Eureka server

   ```bash
	 docker compose up -d eureka-server
   ```
8. Initiate the Spring Authorization Server

   ```bash
   docker compose up -d auth-server
   ```
   
9. Start the following containers:
  -  Spring Cloud Gateway
  -  Spring Resource Servers:
    -  Catalog
    -  Order
    -  Profile
    -  Notification
    -  Payment

  ```bash
  docker compose up -d gateway catalog order profile notification payment
  ```

10. Access the application:

   Open your web browser and visit `http://localhost:8081`.

## Usage

Once the application is running, you can access the various features through the provided user interface. Here are some usage instructions:

- Create an account or log in with your existing credentials.
- Browse the product catalog and add items to your cart.
- Please go ahead and go to the checkout process and provide the necessary details.
- Review your order and complete the payment.
- Track the status of your orders.
- Admins can access the admin panel to manage products, categories, and orders.

## Contributing

Contributions to the E-Commerce project are welcome! If you would like to contribute, please follow these steps:

1. Fork the repository.
2. Create a new branch for your feature or bug fix:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. Make the necessary changes and commit them:
   ```bash
   git commit -m "Add your commit message here"
   ```
4. Push your changes to your forked repository:
   ```bash
   git push origin feature/your-feature-name
   ```
5. Open a pull request to the original repository.

## License

The E-Commerce project is licensed under the MIT License. Please refer to the [LICENSE](LICENSE) file for more information.
```

Feel free to modify and customize the README file according to your specific requirements and project details.
