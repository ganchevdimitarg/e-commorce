# ADR-010: JSON Schema validation of product events

**Date:** 2026-10-21
**Status:** Accepted

## Context

Catalog publishes four product event types — `created`, `updated`, `deleted` and
`category-changed` — as **JSON** to the topics in `ProductTopics`, written to the
transactional outbox (ADR-007) and relayed to Kafka by `OutboxRelay`. The payloads carry
`eventId`, `productId`, `productName`, `price`, `inStock`, `categoryName`, `version`,
`schemaVersion` and `occurredAt`; `category-changed` additionally carries
`previousCategoryName`.

ADR-002 (2025-06) selected Avro plus Confluent Schema Registry as the eventual contract,
but that infrastructure belongs to the deferred `common-events` module and does not exist
in catalog. Until it does, nothing stops a producer-side change from emitting a payload
that no consumer can deserialise, and nothing catches the mistake until a consumer fails
in production.

## Decision

Catalog validates every product event against a **versioned JSON Schema file shipped with
the artifact** before writing the outbox row.

### The schemas

One Draft 2020-12 schema per topic lives at
`classpath:event-schemas/<topic>.v1.json`:

- `additionalProperties: false`, so an unknown field is a violation rather than a silent
  addition consumers must ignore;
- the producer fields are `required`; `categoryName` and `previousCategoryName` are
  nullable (`["string", "null"]`);
- `schemaVersion` is `const: 1`;
- `format: uuid` / `format: date-time` are declared, and the payload types are enforced.

Each `$id` is an absolute URN (`urn:catalog:event:<topic>:v1`) because networknt 3.0.8
rejects the bare `<topic>.v1` identifier the plan sketched as an invalid `$id`.

Adding, removing or retyping a field requires a **new schema file** — `<topic>.v2.json` —
because `additionalProperties: false` makes any v1-compatible change impossible without a
new version. `schemaVersion` on the event selects the version in principle; v1 is the only
version today, so `EventSchemaValidator` loads the `.v1.json` suffix.

### Validation behaviour

`EventSchemaValidator` loads all four schemas in its constructor and fails application
startup with `IllegalStateException` if any is missing. `OutboxEventWriter.write(...)`
calls `validate(topic, event)` **before** serialising and saving the outbox row; a
violation throws `IllegalStateException` naming the topic and the validator messages. The
exception is not caught, so it propagates out of the service method and rolls back the
caller's business transaction — a product change must never commit without its event.

The validator serialises the event with the application's shared `ObjectMapper` and lets
the schema library parse that JSON string. This guarantees the validated document is the
same shape the writer stores, and it bridges the library's Jackson 3 nodes to the
application's Jackson 2 mapper without a second `ObjectMapper`. The cost is one extra
serialisation per event.

Unknown topics are rejected, never silently skipped: a topic with no schema is a wiring
bug, and ignoring it would reintroduce the unvalidated path this ADR closes.

### What this is not

This is a **registry-ready producer contract, not an external registry**. The schemas and
their compatibility rules ship with the artifact; there is no central schema store and no
Avro/Confluent client in catalog. Avro plus Confluent Schema Registry remain deferred to
`common-events` by ADR-002; when that lands, the subject naming below maps directly onto
the registry.

## Amendment 2026-10-22 — version dispatch and compatibility gate

The original decision noted that `schemaVersion` selects a version "in principle" while the
loader only read `.v1.json`. The loader now honours the field:

- `EventSchemaCatalog` scans every `<topic>.v<N>.json` under `classpath:event-schemas`,
  keyed by topic and version. The filename, the schema's `$id`
  (`urn:catalog:event:<topic>:v<N>`) and `properties.schemaVersion.const` must agree; a
  mismatch, duplicate version, unknown topic or missing topic fails startup.
- `EventSchemaValidator` serialises the event, reads its `schemaVersion`, and validates
  the same JSON string against that version. Adding `<topic>.v2.json` is now a data
  change, not a validator change.
- **Compatibility policy: BACKWARD.** `EventSchemaCompatibilityTest` (unit, part of
  `verify`) compares consecutive versions and fails on a removed property, a narrowed
  type, a removed `enum`/`const` value, a newly required property or tightened
  `additionalProperties`. Optional additions pass. `$id` and
  `properties.schemaVersion.const` are exempt because they must change.
- **Registry-ready subjects.** The future subject name is `<topic>-value` (for example
  `catalog.product.created-value`) with version `N`; the `$id` URN and file name are the
  local identifiers. Moving to Apicurio or Confluent changes the storage, not the schema
  shapes.
- A v2 is still required for any added field: `additionalProperties: false` means an
  unversioned addition is a contract change even when it is backward compatible.

## Consequences

- **Invalid payloads fail the transaction.** A schema violation rolls back the product
  write rather than parking an unusable event in the outbox.
- **Missing or inconsistent schemas fail startup.** A packaging mistake, a duplicate
  version or a filename/`$id`/`const` disagreement surfaces at boot, not on the first
  event.
- **Format assertions follow the library defaults.** Draft 2020-12 treats `format` as an
  annotation, and networknt's defaults are left untouched; `uuid` and `date-time` document
  intent but are not the enforcement surface. Type, `required`, `additionalProperties`
  and `const` are enforced and tested.
- **Schema changes are versioned, not edited.** A v1 file is committed and immutable;
  changing the contract adds a v2 file; `schemaVersion` selects it at write time and the
  compatibility gate checks consecutive versions.
- **One extra serialisation and parse per event.** The writer serialises for validation,
  parses the JSON to read `schemaVersion`, then stores the validated string. Product
  mutations are low-frequency; the cost is accepted for validating exactly what is stored.
- **Consumers still need their own validation.** Catalog controls only what it produces;
  consumer-side validation remains outside catalog; Tasks 11–12 cover producer REST
  contracts and mutation testing.

## Alternatives rejected

- **Avro + Confluent Schema Registry now.** ADR-002 defers the shared `common-events`
  infrastructure; adding a per-module registry client would create a second, competing
  contract mechanism.
- **Validating in `OutboxRelay` before the Kafka send.** By then the business transaction
  has committed. A violation could only dead-letter the row (ADR-009), leaving the product
  change applied with no published event.
- **A validation library at consumer deserialisation time only.** The failure would be
  remote and late; the producer would not know it emitted an invalid payload.
- **Open `additionalProperties` in v1.** It would let producers add fields without
  versioning, which is precisely the drift this ADR prevents.
