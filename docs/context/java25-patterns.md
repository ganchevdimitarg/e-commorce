# Java 25 platform feature patterns

## StructuredTaskScope — parallel fan-out
```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var inventory = scope.fork(() -> inventoryClient.get(productId));
    var pricing   = scope.fork(() -> pricingClient.get(productId));
    scope.join().throwIfFailed();
    return new ProductView(inventory.get(), pricing.get());
}
```

## ScopedValue — request-scoped context (replaces ThreadLocal)
```java
static final ScopedValue<RequestContext> CTX = ScopedValue.newInstance();
ScopedValue.where(CTX, new RequestContext(traceId, userId)).run(() -> service.handle(cmd));
```

## Sealed interfaces + exhaustive switch
```java
sealed interface PaymentResult permits Approved, Declined, Pending {}
String label = switch (result) {
    case Approved a -> "approved:" + a.transactionId();
    case Declined d -> "declined:" + d.reason();
    case Pending  p -> "pending:"  + p.retryAfter();
};
```
