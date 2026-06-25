# Kafka — platform conventions

Cross-service eventing policy. catalog and order produce JSON domain events today; the
Avro/Schema-Registry rules below are **aspirational** — they apply once `common-events`
and a Schema Registry exist. For catalog's concrete JSON implementation see
`kafka-patterns.md`; for producer/consumer config see `.claude/context/kafka-setup.md`.

## Topics & consumers
- Topic naming: `<domain>.<entity>.<event>` e.g. `order.payment.completed`.
- Consumer group: `<service>-group` e.g. `order-group`.
- Dead-letter topic: `<original-topic>.DLT` — configure via `@RetryableTopic`.
- Retry: 3 attempts with exponential backoff before DLT; log and alert on DLT arrival.
- All messages carry `traceId` and `correlationId` as headers.
- Use `@KafkaListener` with an explicit `groupId`; never rely on the default group ID.
- Idempotency: check `correlationId` in Redis before processing to avoid duplicate handling.

## Avro / Schema Registry (aspirational)
- All event schemas live in `common-events/src/main/avro/<domain>/` as `.avsc` files.
- Schema subject: `<topic>-value` e.g. `order.payment.completed-value`.
- Compatibility mode: **BACKWARD** — consumers on the old schema can read new messages.
- Safe evolution:
  - New field: always add a `"default"` — never a field without one.
  - Never remove, rename, or change the type of an existing field — add a new one.
  - Never change a field from optional to required.
- Register the schema before producing; CI runs `mvn schema-registry:register` on the
  `common-events` build.
- Detail on demand: `avro-patterns.md`.
