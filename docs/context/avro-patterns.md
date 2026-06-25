# Avro / Schema Registry Patterns

## Schema file location
`common-events/src/main/avro/<domain>/<EventName>.avsc`

## Canonical schema example
```json
{
  "type": "record",
  "name": "PaymentCompletedEvent",
  "namespace": "com.example.events.order",
  "fields": [
    {"name": "orderId",       "type": "string"},
    {"name": "traceId",       "type": "string"},
    {"name": "correlationId", "type": "string"},
    {"name": "amount",        "type": "double", "default": 0.0},
    {"name": "currency",      "type": "string", "default": "EUR"}
  ]
}
```

## Evolution rules
- New field: always add `"default"` — never omit
- Never remove, rename, or change type of an existing field — add a new one
- Never change optional → required
- Compatibility mode: **BACKWARD**

## Commands
```bash
# Register schema
./mvnw schema-registry:register -pl common-events

# Check compatibility before registering
curl http://localhost:8081/compatibility/subjects/order.payment.completed-value/versions/latest \
  -d @common-events/src/main/avro/order/PaymentCompletedEvent.avsc \
  -H "Content-Type: application/json"

# Generate Java classes from schema
./mvnw generate-sources -pl common-events
```
