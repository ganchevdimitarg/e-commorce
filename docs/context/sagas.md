# Distributed transactions — choreography saga

> Aspirational: applies once cross-service Kafka eventing exists. Today catalog and order
> publish domain events, but no multi-service saga is wired yet. Do not scaffold a saga
> without a concrete cross-service flow.

Never use 2-phase commit or synchronous cross-service writes. Use choreography-based
sagas via Kafka events:

- Each service publishes a success or failure event after its local transaction commits.
- Compensating transactions are triggered by failure events — no central orchestrator.
- Saga state is reconstructed by replaying events — never stored in a shared table.
- Document each flow in `docs/sagas/<name>.md` with the event sequence and its
  compensations.
