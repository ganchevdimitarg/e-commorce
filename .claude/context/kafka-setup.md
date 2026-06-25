# Kafka setup context — load with: @.claude/context/kafka-setup.md

## Dependencies (in common-events/pom.xml)
```xml
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-avro-serializer</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

## application.yml producer config
```yaml
spring.kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  producer:
    key-serializer: org.apache.kafka.common.serialization.StringSerializer
    value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
  consumer:
    key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
    auto-offset-reset: earliest
  properties:
    schema.registry.url: ${SCHEMA_REGISTRY_URL:http://localhost:8081}
    specific.avro.reader: true
```

## Producer pattern
```java
@RequiredArgsConstructor @Slf4j @Service
public class OrderEventPublisher {
    private final KafkaTemplate<String, SpecificRecord> kafka;

    public void publish(OrderPlacedEvent event) {
        var record = new ProducerRecord<String, SpecificRecord>(
            "order.order.placed", event.getOrderId(), event);
        record.headers()
            .add("traceId",       event.getTraceId().getBytes())
            .add("correlationId", event.getCorrelationId().getBytes());
        kafka.send(record).whenComplete((r, ex) -> {
            if (ex != null) log.error("Failed to publish {}", event, ex);
        });
    }
}
```

## Consumer pattern with idempotency + retry
```java
@RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
@KafkaListener(topics = "order.payment.completed", groupId = "<service-name>-group")
public void onPaymentCompleted(PaymentCompletedEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    MDC.put("traceId",       event.getTraceId().toString());
    MDC.put("correlationId", event.getCorrelationId().toString());
    try {
        String key = "processed:" + event.getCorrelationId();
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24)))) {
            notificationService.notify(event);
        }
    } finally {
        MDC.clear();
    }
}
```
