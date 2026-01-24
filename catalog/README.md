# Catalog Service

The Catalog Service manages the product catalog, inventory, categories, and product-related information for the e-commerce platform.

## Overview

This service handles:
- Product management (CRUD operations)
- Category management
- Inventory tracking
- Product search and filtering
- Product pricing
- Product images and media
- Stock management

## Technology Stack

- **Framework**: Spring Boot
- **Database**: PostgreSQL/MySQL
- **Caching**: Redis (optional)
- **Search**: Elasticsearch (optional)
- **Service Discovery**: Eureka Client
- **Configuration**: Spring Cloud Config Client

## API Endpoints

### Products

#### Get All Products
```http
GET /api/products?page=0&size=20&sort=name,asc
```

**Response:**
```json
{
  "content": [
    {
      "id": "uuid",
      "name": "Product Name",
      "description": "Product description",
      "price": 99.99,
      "categoryId": "category-uuid",
      "sku": "PROD-001",
      "stockQuantity": 100,
      "imageUrl": "https://example.com/image.jpg",
      "active": true,
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z"
    }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "size": 20,
  "number": 0
}
```

#### Get Product by ID
```http
GET /api/products/{id}
```

#### Create Product
```http
POST /api/products
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "name": "New Product",
  "description": "Product description",
  "price": 49.99,
  "categoryId": "category-uuid",
  "sku": "PROD-002",
  "stockQuantity": 50,
  "imageUrl": "https://example.com/image.jpg"
}
```

#### Update Product
```http
PUT /api/products/{id}
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "name": "Updated Product Name",
  "price": 54.99,
  "stockQuantity": 45
}
```

#### Delete Product
```http
DELETE /api/products/{id}
Authorization: Bearer {admin_token}
```

#### Search Products
```http
GET /api/products/search?q=laptop&category=electronics&minPrice=500&maxPrice=2000
```

### Categories

#### Get All Categories
```http
GET /api/categories
```

**Response:**
```json
[
  {
    "id": "uuid",
    "name": "Electronics",
    "description": "Electronic devices and accessories",
    "parentId": null,
    "imageUrl": "https://example.com/electronics.jpg",
    "active": true,
    "productCount": 150
  }
]
```

#### Get Category by ID
```http
GET /api/categories/{id}
```

#### Create Category
```http
POST /api/categories
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "name": "Laptops",
  "description": "Laptop computers",
  "parentId": "electronics-uuid",
  "imageUrl": "https://example.com/laptops.jpg"
}
```

#### Get Products by Category
```http
GET /api/categories/{id}/products?page=0&size=20
```

### Inventory

#### Check Stock
```http
GET /api/inventory/{productId}
```

**Response:**
```json
{
  "productId": "uuid",
  "quantity": 100,
  "reserved": 5,
  "available": 95,
  "lastUpdated": "2024-01-15T10:30:00Z"
}
```

#### Update Stock
```http
PUT /api/inventory/{productId}
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "quantity": 150,
  "operation": "SET" // or "ADD", "SUBTRACT"
}
```

#### Reserve Stock (for orders)
```http
POST /api/inventory/reserve
Authorization: Bearer {service_token}
Content-Type: application/json

{
  "items": [
    {
      "productId": "uuid",
      "quantity": 2
    }
  ],
  "orderId": "order-uuid"
}
```

#### Release Reserved Stock
```http
POST /api/inventory/release
Authorization: Bearer {service_token}
Content-Type: application/json

{
  "orderId": "order-uuid"
}
```

### Product Reviews (Optional)

#### Get Product Reviews
```http
GET /api/products/{productId}/reviews?page=0&size=10
```

#### Add Review
```http
POST /api/products/{productId}/reviews
Authorization: Bearer {user_token}
Content-Type: application/json

{
  "rating": 5,
  "comment": "Great product!",
  "title": "Excellent quality"
}
```

## Configuration

### application.yml
```yaml
server:
  port: 8082

spring:
  application:
    name: catalog-service
  datasource:
    url: jdbc:postgresql://localhost:5432/catalog_db
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  redis:
    host: localhost
    port: 6379
    enabled: ${REDIS_ENABLED:false}

elasticsearch:
  enabled: ${ELASTICSEARCH_ENABLED:false}
  host: localhost
  port: 9200

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

cache:
  product:
    ttl: 3600 # 1 hour
  category:
    ttl: 7200 # 2 hours
```

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `DB_USERNAME` | Database username | `catalog_user` |
| `DB_PASSWORD` | Database password | `secure_password` |
| `REDIS_ENABLED` | Enable Redis caching | `true` |
| `ELASTICSEARCH_ENABLED` | Enable Elasticsearch search | `true` |

## Database Schema

### Products Table
```sql
CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    category_id UUID REFERENCES categories(id),
    sku VARCHAR(100) UNIQUE NOT NULL,
    stock_quantity INTEGER DEFAULT 0,
    image_url VARCHAR(500),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_active ON products(active);
```

### Categories Table
```sql
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    parent_id UUID REFERENCES categories(id),
    image_url VARCHAR(500),
    active BOOLEAN DEFAULT TRUE,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_parent ON categories(parent_id);
```

### Inventory Table
```sql
CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    product_id UUID REFERENCES products(id) UNIQUE,
    quantity INTEGER NOT NULL DEFAULT 0,
    reserved INTEGER NOT NULL DEFAULT 0,
    available INTEGER GENERATED ALWAYS AS (quantity - reserved) STORED,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT positive_quantity CHECK (quantity >= 0),
    CONSTRAINT positive_reserved CHECK (reserved >= 0),
    CONSTRAINT valid_reservation CHECK (reserved <= quantity)
);

CREATE INDEX idx_inventory_product ON inventory(product_id);
```

### Product Images Table (Optional)
```sql
CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    product_id UUID REFERENCES products(id),
    url VARCHAR(500) NOT NULL,
    alt_text VARCHAR(255),
    display_order INTEGER DEFAULT 0,
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Reviews Table (Optional)
```sql
CREATE TABLE product_reviews (
    id UUID PRIMARY KEY,
    product_id UUID REFERENCES products(id),
    user_id UUID NOT NULL,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(255),
    comment TEXT,
    verified_purchase BOOLEAN DEFAULT FALSE,
    helpful_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reviews_product ON product_reviews(product_id);
CREATE INDEX idx_reviews_user ON product_reviews(user_id);
```

## Business Logic

### Stock Management

#### Reserve Stock Flow
1. Order Service requests stock reservation
2. Catalog Service validates product availability
3. If available, update inventory reserved count
4. Return reservation confirmation
5. If order is cancelled, release reserved stock

#### Low Stock Alerts
- Trigger alert when stock falls below threshold (e.g., 10 units)
- Notify inventory management team
- Optionally disable product if out of stock

### Product Pricing
- Support for multiple price types (regular, sale, promotional)
- Price history tracking
- Currency support (if multi-currency)

## Running the Service

### Local Development
```bash
cd catalog
mvn spring-boot:run
```

### With Docker
```bash
docker build -t catalog-service .
docker run -p 8082:8082 catalog-service
```

### With Docker Compose
```bash
docker-compose up catalog
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

**Get Products:**
```bash
curl http://localhost:8082/api/products
```

**Create Product:**
```bash
curl -X POST http://localhost:8082/api/products \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Product",
    "description": "Test description",
    "price": 29.99,
    "sku": "TEST-001",
    "stockQuantity": 100
  }'
```

**Search Products:**
```bash
curl "http://localhost:8082/api/products/search?q=laptop&minPrice=500"
```

## Caching Strategy

### Cached Entities
- Product details (1 hour TTL)
- Category tree (2 hours TTL)
- Product search results (15 minutes TTL)

### Cache Invalidation
- On product update/delete
- On category changes
- On stock updates (for availability)

## Event Publishing

The Catalog Service publishes events for:
- `ProductCreated`
- `ProductUpdated`
- `ProductDeleted`
- `StockUpdated`
- `LowStockAlert`

These events are consumed by other services (Order, Notification, etc.)

## Performance Optimization

1. **Database Indexing** - Indexes on frequently queried columns
2. **Pagination** - All list endpoints support pagination
3. **Caching** - Redis caching for frequently accessed data
4. **Search Optimization** - Elasticsearch for advanced search
5. **Image CDN** - Product images served from CDN
6. **Query Optimization** - N+1 query prevention with JPA fetch strategies

## Monitoring

### Metrics to Monitor
- Product search response time
- Cache hit/miss ratio
- Database connection pool usage
- Stock update frequency
- API endpoint response times

### Health Checks
```http
GET /actuator/health
```

## Troubleshooting

### Common Issues

**Issue: "Product not found"**
- Solution: Verify product ID and ensure product exists in database

**Issue: "Insufficient stock"**
- Solution: Check inventory table for actual available quantity

**Issue: "Slow search queries"**
- Solution: Enable Elasticsearch or add database indexes

**Issue: "Cache not updating"**
- Solution: Check Redis connection and cache invalidation logic

## Best Practices

1. **Always validate stock** before confirming orders
2. **Use transactions** for inventory updates
3. **Implement optimistic locking** to prevent concurrent update issues
4. **Version product data** for audit trails
5. **Soft delete products** instead of hard deletes
6. **Validate SKU uniqueness** before creating products
7. **Use DTOs** to prevent over-fetching data

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
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
</dependencies>
```

## License

This service is part of the e-commerce microservices platform and follows the same license as the parent project.
