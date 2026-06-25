# Order Service

The Order Service manages the entire order lifecycle, from cart management to order fulfillment, in the e-commerce platform.

## Overview

This service handles:
- Shopping cart management
- Order creation and processing
- Order status tracking
- Order history
- Order cancellation and refunds
- Integration with Payment and Inventory services
- Order notifications

## Technology Stack

- **Framework**: Spring Boot
- **Database**: PostgreSQL/MySQL
- **Messaging**: RabbitMQ/Kafka
- **Service Discovery**: Eureka Client
- **Configuration**: Spring Cloud Config Client
- **Resilience**: Resilience4j (Circuit Breaker, Retry)

## API Endpoints

### Shopping Cart

#### Get Cart
```http
GET /api/cart
Authorization: Bearer {user_token}
```

**Response:**
```json
{
  "id": "cart-uuid",
  "userId": "user-uuid",
  "items": [
    {
      "productId": "product-uuid",
      "productName": "Product Name",
      "quantity": 2,
      "price": 49.99,
      "subtotal": 99.98
    }
  ],
  "totalItems": 2,
  "totalAmount": 99.98,
  "lastUpdated": "2024-01-15T10:30:00Z"
}
```

#### Add to Cart
```http
POST /api/cart/items
Authorization: Bearer {user_token}
Content-Type: application/json

{
  "productId": "product-uuid",
  "quantity": 1
}
```

#### Update Cart Item
```http
PUT /api/cart/items/{productId}
Authorization: Bearer {user_token}
Content-Type: application/json

{
  "quantity": 3
}
```

#### Remove from Cart
```http
DELETE /api/cart/items/{productId}
Authorization: Bearer {user_token}
```

#### Clear Cart
```http
DELETE /api/cart
Authorization: Bearer {user_token}
```

### Orders

#### Create Order (Checkout)
```http
POST /api/orders
Authorization: Bearer {user_token}
Content-Type: application/json

{
  "shippingAddress": {
    "street": "123 Main St",
    "city": "New York",
    "state": "NY",
    "zipCode": "10001",
    "country": "USA"
  },
  "billingAddress": {
    "street": "123 Main St",
    "city": "New York",
    "state": "NY",
    "zipCode": "10001",
    "country": "USA"
  },
  "paymentMethod": "CREDIT_CARD",
  "notes": "Please deliver before 5 PM"
}
```

**Response:**
```json
{
  "orderId": "order-uuid",
  "orderNumber": "ORD-2024-001234",
  "status": "PENDING_PAYMENT",
  "totalAmount": 99.98,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### Get Order by ID
```http
GET /api/orders/{orderId}
Authorization: Bearer {user_token}
```

**Response:**
```json
{
  "id": "order-uuid",
  "orderNumber": "ORD-2024-001234",
  "userId": "user-uuid",
  "status": "CONFIRMED",
  "items": [
    {
      "productId": "product-uuid",
      "productName": "Product Name",
      "quantity": 2,
      "price": 49.99,
      "subtotal": 99.98
    }
  ],
  "shippingAddress": {
    "street": "123 Main St",
    "city": "New York",
    "state": "NY",
    "zipCode": "10001",
    "country": "USA"
  },
  "subtotal": 99.98,
  "tax": 8.50,
  "shippingCost": 5.99,
  "totalAmount": 114.47,
  "paymentStatus": "PAID",
  "paymentMethod": "CREDIT_CARD",
  "trackingNumber": "TRK123456789",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:35:00Z"
}
```

#### Get User Orders
```http
GET /api/orders?page=0&size=10&sort=createdAt,desc
Authorization: Bearer {user_token}
```

#### Cancel Order
```http
POST /api/orders/{orderId}/cancel
Authorization: Bearer {user_token}
Content-Type: application/json

{
  "reason": "Changed my mind"
}
```

#### Track Order
```http
GET /api/orders/{orderId}/tracking
Authorization: Bearer {user_token}
```

**Response:**
```json
{
  "orderId": "order-uuid",
  "orderNumber": "ORD-2024-001234",
  "currentStatus": "SHIPPED",
  "trackingNumber": "TRK123456789",
  "carrier": "UPS",
  "estimatedDelivery": "2024-01-20T00:00:00Z",
  "statusHistory": [
    {
      "status": "PENDING",
      "timestamp": "2024-01-15T10:30:00Z",
      "note": "Order placed"
    },
    {
      "status": "CONFIRMED",
      "timestamp": "2024-01-15T10:35:00Z",
      "note": "Payment confirmed"
    },
    {
      "status": "PROCESSING",
      "timestamp": "2024-01-15T11:00:00Z",
      "note": "Order being prepared"
    },
    {
      "status": "SHIPPED",
      "timestamp": "2024-01-16T09:00:00Z",
      "note": "Package shipped"
    }
  ]
}
```

### Order Statistics (Admin)

#### Get Order Statistics
```http
GET /api/orders/stats?startDate=2024-01-01&endDate=2024-01-31
Authorization: Bearer {admin_token}
```

**Response:**
```json
{
  "totalOrders": 1250,
  "totalRevenue": 125000.00,
  "averageOrderValue": 100.00,
  "ordersByStatus": {
    "PENDING": 50,
    "CONFIRMED": 200,
    "SHIPPED": 800,
    "DELIVERED": 180,
    "CANCELLED": 20
  }
}
```

## Configuration

### application.yml
```yaml
server:
  port: 8083

spring:
  application:
    name: order-service
  datasource:
    url: jdbc:postgresql://localhost:5432/order_db
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  rabbitmq:
    host: localhost
    port: 5672
    username: ${RABBITMQ_USERNAME}
    password: ${RABBITMQ_PASSWORD}

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

resilience4j:
  circuitbreaker:
    instances:
      catalogService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10000
      paymentService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10000

order:
  cancellation:
    allowed-hours: 24 # Hours after order creation
  auto-complete:
    days: 7 # Days after delivery to auto-complete
```

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `DB_USERNAME` | Database username | `order_user` |
| `DB_PASSWORD` | Database password | `secure_password` |
| `RABBITMQ_USERNAME` | RabbitMQ username | `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `guest` |

## Database Schema

### Orders Table
```sql
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    subtotal DECIMAL(10, 2) NOT NULL,
    tax DECIMAL(10, 2) DEFAULT 0,
    shipping_cost DECIMAL(10, 2) DEFAULT 0,
    total_amount DECIMAL(10, 2) NOT NULL,
    payment_status VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50),
    payment_id VARCHAR(255),
    tracking_number VARCHAR(100),
    carrier VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created ON orders(created_at);
CREATE INDEX idx_orders_number ON orders(order_number);
```

### Order Items Table
```sql
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    subtotal DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT positive_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);
```

### Shipping Address Table
```sql
CREATE TABLE shipping_addresses (
    id UUID PRIMARY KEY,
    order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
    recipient_name VARCHAR(255),
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    zip_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL,
    phone VARCHAR(20)
);
```

### Billing Address Table
```sql
CREATE TABLE billing_addresses (
    id UUID PRIMARY KEY,
    order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    zip_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL
);
```

### Shopping Cart Table
```sql
CREATE TABLE shopping_carts (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_carts_user ON shopping_carts(user_id);
```

### Cart Items Table
```sql
CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID REFERENCES shopping_carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT positive_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);
CREATE UNIQUE INDEX idx_cart_items_cart_product ON cart_items(cart_id, product_id);
```

### Order Status History Table
```sql
CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    note TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE INDEX idx_status_history_order ON order_status_history(order_id);
```

## Order States

```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED → COMPLETED
                ↓
            CANCELLED
```

### State Descriptions
- **PENDING**: Order created, awaiting payment confirmation
- **CONFIRMED**: Payment confirmed, ready for processing
- **PROCESSING**: Order being prepared for shipment
- **SHIPPED**: Order has been shipped
- **DELIVERED**: Order delivered to customer
- **COMPLETED**: Order completed (auto-completed after 7 days)
- **CANCELLED**: Order cancelled by user or system

## Business Logic

### Order Creation Flow
1. Validate cart items
2. Check product availability (call Catalog Service)
3. Reserve inventory (call Catalog Service)
4. Calculate totals (subtotal, tax, shipping)
5. Create order record
6. Process payment (call Payment Service)
7. If payment successful → Confirm order
8. If payment fails → Release reserved inventory
9. Send order confirmation (publish event to Notification Service)
10. Clear shopping cart

### Order Cancellation Rules
- Can be cancelled within 24 hours of creation
- Cannot cancel if status is SHIPPED or DELIVERED
- Refund initiated automatically
- Inventory reservation released
- Notification sent to user

### Inventory Management
- Reserve stock when order is created
- Release stock if payment fails or order is cancelled
- Deduct stock when order is confirmed

## Event-Driven Architecture

### Published Events
- `OrderCreated` - When new order is placed
- `OrderConfirmed` - When payment is confirmed
- `OrderShipped` - When order is shipped
- `OrderDelivered` - When order is delivered
- `OrderCancelled` - When order is cancelled
- `OrderCompleted` - When order is auto-completed

### Subscribed Events
- `PaymentProcessed` - From Payment Service
- `PaymentFailed` - From Payment Service
- `StockReserved` - From Catalog Service
- `StockReleased` - From Catalog Service

## Resilience Patterns

### Circuit Breaker
- Applied to Catalog Service calls
- Applied to Payment Service calls
- Fallback: Return cached data or error message

### Retry Logic
- Retry failed inventory reservations (max 3 attempts)
- Exponential backoff for payment processing
- Dead letter queue for failed messages

### Timeout Configuration
- Catalog Service calls: 5 seconds
- Payment Service calls: 10 seconds
- Database queries: 30 seconds

## Running the Service

### Local Development
```bash
cd order
mvn spring-boot:run
```

### With Docker
```bash
docker build -t order-service .
docker run -p 8083:8083 order-service
```

### With Docker Compose
```bash
docker-compose up order
```

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

### Manual Testing with cURL

**Add to Cart:**
```bash
curl -X POST http://localhost:8083/api/cart/items \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"productId": "product-uuid", "quantity": 2}'
```

**Create Order:**
```bash
curl -X POST http://localhost:8083/api/orders \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "shippingAddress": {
      "street": "123 Main St",
      "city": "New York",
      "state": "NY",
      "zipCode": "10001",
      "country": "USA"
    },
    "paymentMethod": "CREDIT_CARD"
  }'
```

## Monitoring

### Key Metrics
- Orders per minute
- Average order value
- Order completion rate
- Order cancellation rate
- Service response times
- Circuit breaker status

### Health Checks
```http
GET /actuator/health
```

## Troubleshooting

### Common Issues

**Issue: "Insufficient inventory"**
- Solution: Check Catalog Service availability and stock levels

**Issue: "Payment failed"**
- Solution: Verify Payment Service is running and check payment details

**Issue: "Order stuck in PENDING"**
- Solution: Check payment processing status and event queue

**Issue: "Cannot cancel order"**
- Solution: Verify order status and cancellation time window

## Best Practices

1. **Always use transactions** for order creation
2. **Implement idempotency** for order operations
3. **Validate stock before checkout**
4. **Handle payment failures gracefully**
5. **Send notifications asynchronously**
6. **Log all order state changes**
7. **Implement order number generation strategy**
8. **Use pessimistic locking** for critical operations

## Dependencies

Key Maven dependencies:
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot2</artifactId>
    </dependency>
</dependencies>
```

## License

This service is part of the e-commerce microservices platform and follows the same license as the parent project.
