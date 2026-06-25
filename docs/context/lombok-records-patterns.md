# Lombok and Records patterns

## Records — default for all immutable types
```java
record CreateOrderCommand(UUID customerId, List<OrderItem> items) {
    CreateOrderCommand { Objects.requireNonNull(customerId); items = List.copyOf(items); }
}
record OrderResponse(UUID id, String status, BigDecimal total) {}
record MoneyAmount(BigDecimal value, Currency currency) {}         // value object
record PageQuery(int page, int size, String sortBy) {}             // query params
record PaymentCompletedEvent(UUID orderId, String traceId, String correlationId) {} // Kafka event
```

## @Value+@Builder — only when record is insufficient
```java
@Value @Builder
public class ComplexDto { /* needs @JsonDeserialize(builder=...) or no-arg ctor */ }
```

## JPA entities — explicit Lombok, never @Data
```java
@Entity @Getter @Setter @NoArgsConstructor @Table(name = "orders")
public class Order {
    @Id @GeneratedValue UUID id;
    // implement equals/hashCode on @NaturalId or business key only
}
```
