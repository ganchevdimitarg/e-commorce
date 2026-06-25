# Idempotency Pattern

All mutating REST endpoints (POST, PUT, PATCH) that cross a service boundary must support
`Idempotency-Key` header.

## Controller pattern
```java
@PostMapping("/orders")
public ResponseEntity<OrderResponse> create(
        @RequestHeader("Idempotency-Key") UUID idempotencyKey,
        @RequestBody @Valid CreateOrderCommand cmd) {

    String redisKey = "idempotency:<service-name>:" + idempotencyKey;

    // Check cache for existing response
    OrderResponse cached = redis.opsForValue().get(redisKey);
    if (cached != null) return ResponseEntity.ok(cached);

    // Process and cache
    OrderResponse response = orderService.create(cmd);
    redis.opsForValue().set(redisKey, response, Duration.ofHours(24));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

## Rules
- Key scoped to service only — never per-user
- TTL: 24h
- Return identical response on repeat — never re-process
- Log duplicate detection at DEBUG level
