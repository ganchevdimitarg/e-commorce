# Kafka patterns

Catalog is **producer-only** — no consumers. JSON over Kafka via `JsonSerializer` (no binary
serialisation, no external registry).

## Producer config

`KafkaProducerConfig` builds a typed `KafkaTemplate<String, ProductEvent>`:

```java
@Configuration
public class KafkaProducerConfig {

    @Value("${kafka.bootstrapAddress}")
    private String bootstrapAddress;

    private Map<String, Object> producerConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    @Bean
    public ProducerFactory<String, ProductEvent> productEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfig());
    }

    @Bean
    public KafkaTemplate<String, ProductEvent> productEventKafkaTemplate(
            ProducerFactory<String, ProductEvent> productEventProducerFactory) {
        KafkaTemplate<String, ProductEvent> template = new KafkaTemplate<>(productEventProducerFactory);
        template.setObservationEnabled(true);   // propagates trace context
        return template;
    }
}
```

## Sealed events

```java
public sealed interface ProductEvent
        permits ProductEvent.ProductCreated, ProductEvent.ProductUpdated, ProductEvent.ProductDeleted {
    String productName();
    record ProductCreated(String productName) implements ProductEvent {}
    record ProductUpdated(String productName) implements ProductEvent {}
    record ProductDeleted(String productName) implements ProductEvent {}
}
```

## After-commit publishing

`ProductServiceImpl.publishAfterCommit()` defers the Kafka send until the database transaction
commits, preventing events for rolled-back writes:

```java
private void publishAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { action.run(); }
        });
    } else {
        action.run();   // fallback for non-transactional context
    }
}
```

Usage: `publishAfterCommit(() -> productEventPublisher.publishCreated(product.getName()))`.

## Error handling

`ProductEventPublisher.send()` logs failures and increments a Micrometer counter:

```java
kafkaTemplate.send(topic, key, event)
        .whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send event to {}: {}", topic, ex.getMessage(), ex);
                meterRegistry.counter("catalog.event.send.failed", "topic", topic).increment();
            }
        });
```

## Topic naming

`catalog.<entity>.<action>` — constants in `ProductEventPublisher`:

| Constant | Topic |
|---|---|
| `CREATED` | `catalog.product.created` |
| `UPDATED` | `catalog.product.updated` |
| `DELETED` | `catalog.product.deleted` |

The message key is the product name (natural partition key).

## Kafka idempotency

- **Producer-side**: `enable.idempotence=true` in `KafkaProducerConfig` (exactly-once per partition).
- **Consumer-side** (future): Redis `SETNX` deduplication — see [idempotency.md](idempotency.md).

## Dead letter queue (DLQ)

Catalog is currently producer-only — no consumer DLQ is configured. When adding consumers, use:

```java
@RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
public void handle(ProductEvent event) { /* ... */ }

@DltHandler
public void handleDlt(ProductEvent event) { log.error("DLT: {}", event); }
```

Alternative: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`. DLT topic naming:
`<original-topic>.DLT` (e.g. `catalog.product.created.DLT`).
