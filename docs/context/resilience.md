# Resilience4j Patterns

Every outbound HTTP call: `@CircuitBreaker` + `@Bulkhead` + `@TimeLimiter`.

## Default thresholds (override per-service in application.yml)
```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventory:
        slidingWindowSize: 10
        failureRateThreshold: 50
        slowCallDurationThreshold: 2s
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 5
  bulkhead:
    instances:
      inventory:
        maxConcurrentCalls: 10
  timelimiter:
    instances:
      inventory:
        timeoutDuration: 5s
```

## Java pattern
```java
@CircuitBreaker(name = "inventory", fallbackMethod = "inventoryFallback")
@Bulkhead(name = "inventory")
@TimeLimiter(name = "inventory")
public CompletableFuture<InventoryResponse> getInventory(UUID productId) {
    return CompletableFuture.supplyAsync(() -> inventoryClient.get(productId));
}

public CompletableFuture<InventoryResponse> inventoryFallback(UUID productId, Throwable t) {
    log.warn("Inventory circuit open for {}: {}", productId, t.getMessage());
    return CompletableFuture.completedFuture(InventoryResponse.unavailable());
}
```
