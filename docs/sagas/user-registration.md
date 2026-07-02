## Saga: User registration & deletion

### Trigger
- Registration: `POST /api/v1/auth/register` on the auth service.
- Deletion: `DELETE /api/v1/auth/account` on the auth service (JWT subject = userId).

### Steps & events
**Registration**
1. Auth: persist a `user_credentials` row (Postgres, id = `userId`), hash the
   password → publishes `UserRegisteredEvent` to `auth.user.registered`.
2. Profile: consumes `UserRegisteredEvent` (group `profile-group`), inserts a
   profile shell keyed by `userId` (Mongo `profiles`) — idempotent on `userId`.
3. Client: `POST /api/v1/profile/payment-setup` creates the payment customer and
   attaches a card (best-effort; identity from the JWT subject).

**Deletion**
1. Auth: soft-delete the `user_credentials` row (`deleted_at = now()`, `enabled =
   false`) → publishes `UserDeletedEvent` to `auth.user.deleted`.
2. Profile: consumes `UserDeletedEvent`, soft-deletes the profile
   (`deletedAt = now()`) and runs best-effort payment-customer teardown.

### Compensation (on failure at step N)
- Profile consumer failure (parse or processing) → retried twice, then routed to
  `auth.user.registered.DLT` / `auth.user.deleted.DLT` for manual replay. The auth
  credential write is authoritative and is not rolled back.
- Payment teardown failure during deletion → logged and skipped; the profile
  soft-delete still completes (payment cleanup is best-effort, never blocking).

### Invariants
- Exactly one field is shared across services: `userId`. Profile holds no password
  or roles; those live only in auth (`email`/`roles` are read from the JWT).
- Soft-delete only — no hard `DELETE`; all reads filter on the not-deleted state.

### Notes
- Topics: `auth.user.registered`, `auth.user.deleted` (JSON, no type headers).
  DLTs: `<topic>.DLT`. Consumer group: `profile-group`.
- Idempotency key: `userId` — the profile shell insert is a no-op if a profile for
  that `userId` already exists, so redelivery is safe.
- Profile listener processing requires `@EnableKafka` on the consumer config
  (Boot 4 does not auto-activate `@KafkaListener` from a container factory alone).
