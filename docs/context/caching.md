# Caching Strategy

Do NOT use Spring `@Cacheable` — use explicit Redis cache-aside.

## Cache-aside pattern
```java
private String key(UUID id) { return "<service-name>:order:" + id; }

public Order getOrder(UUID id) {
    return Optional.ofNullable(redis.opsForValue().get(key(id)))
        .orElseGet(() -> {
            var order = repo.findById(id).orElseThrow(() -> new NotFoundException("Order", id));
            redis.opsForValue().set(key(id), order, Duration.ofHours(24));
            return order;
        });
}
```

## Write-through pattern
```java
public Order updateOrder(UUID id, UpdateOrderCommand cmd) {
    var order = repo.findById(id).orElseThrow(() -> new NotFoundException("Order", id));
    // ... apply changes ...
    repo.save(order);
    redis.opsForValue().set(key(id), order, Duration.ofHours(24)); // update cache
    return order;
}
```

## Event-driven invalidation
```java
@KafkaListener(topics = "product.product.updated", groupId = "<service-name>-group")
void onProductUpdated(ProductUpdatedEvent event) {
    redis.delete("<service-name>:product:" + event.productId());
}
```

## What to cache / not cache
| Cache | Do not cache |
|---|---|
| Read-heavy, rarely mutated (catalogue, config) | Strongly consistent data (balances, inventory) |
| Expensive computed results | Data the service owns at acceptable latency |
| Idempotency keys + correlation IDs | Anything with unclear invalidation |
