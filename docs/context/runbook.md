# Runbook — common incident commands

```bash
# Find a trace across services
grep '"traceId":"<id>"' /var/log/<service>/app.log | jq .

# Replay a DLT message
kafka-console-consumer --topic order.payment.completed.DLT --from-beginning \
  | kafka-console-producer --topic order.payment.completed

# Flush a Redis key
redis-cli DEL "catalog:product:<uuid>"

# Flyway repair (after a failed migration)
./mvnw flyway:repair -f <module>/pom.xml
./mvnw flyway:migrate -f <module>/pom.xml

# Check Kafka consumer lag
kafka-consumer-groups --describe --group <service>-group \
  --bootstrap-server localhost:9092

# Check Schema Registry compatibility (once common-events exists)
curl http://localhost:8081/compatibility/subjects/order.payment.completed-value/versions/latest \
  -d @common-events/src/main/avro/order/PaymentCompletedEvent.avsc \
  -H "Content-Type: application/json"
```

Build a single module standalone (the root reactor cannot build until every module is on
Boot 4): `./mvnw -f <module>/pom.xml clean verify`.
