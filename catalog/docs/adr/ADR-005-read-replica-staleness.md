# ADR-005: Read-replica staleness policy and read-your-writes gap

**Date:** 2026-06-23  
**Status:** Accepted

## Context

Phase 7 introduced a read-replica routing layer (`DataSourceRouter` +
`RoutingDataSourceConfig`) that directs `@Transactional(readOnly = true)` queries
to a PostgreSQL streaming replica and all write transactions plus Flyway migrations
to the primary. A health probe degrades read traffic back to the primary when the
replica is unreachable.

This creates an inherent staleness window: the replica lags the primary by the
duration of asynchronous streaming replication (typically single-digit milliseconds
under normal load, but unbounded under network partition or replica overload).

## Decision

### Accepted staleness model

1. **Browse / list reads** (`getProducts`, `getProductByName`,
   `getProductsByCategory`, etc.) are routed to the replica. Mild staleness
   (sub-second under normal conditions) is acceptable for catalogue browsing.

2. **Writes and migrations always target the primary.** `createProduct`,
   `updateProduct`, `deleteProduct`, and all Flyway versioned migrations use the
   writer datasource unconditionally.

3. **Replica outage degrades to the primary.** When the health probe
   (`ReadReplicaHealthIndicator`) detects a replica connection failure the
   `DataSourceRouter` falls back to the writer for read-only transactions. This
   avoids 500 errors at the cost of additional load on the primary. A fast
   connect-timeout bounds the detection window.

4. **Soft-delete filtering (`deleted_at IS NULL`) applies identically on both
   nodes** because it is encoded in JPA `@Where` clauses / repository queries,
   not at the datasource level.

### Known gap: read-after-write is NOT guaranteed

The Phase 5 Redis caching layer uses **cache-aside with eviction on write**, not
write-through population:

- `getProductById` is annotated `@Cacheable(cacheNames = "product", key = "#id")`
  -- it populates the cache on a **read miss** only.
- `createProduct` has **no** cache annotation (no `@CachePut`).
- `updateProduct` and `deleteProduct` use
  `@CacheEvict(cacheNames = "product", allEntries = true)` -- they **evict** the
  entire product cache on write but do **not** populate it with the new value.

Consequence: immediately after a write, the cache entry for the affected product is
absent. A subsequent `getProductById` call is a cache miss. Because that method is
`@Transactional(readOnly = true)`, the miss is served from the **replica**, which
may not yet have replicated the write. The caller can therefore observe stale data
(the pre-update state, or a 404 for a just-created product).

**The Phase 5 cache does not mitigate the read-after-write staleness introduced by
Phase 7. This is a known gap.**

### Options to close the gap (for platform owner)

| Option | Approach | Trade-off |
|---|---|---|
| **Write-through cache** | Add `@CachePut` on `createProduct` / `updateProduct` (or manually put into the cache after save) so the next read is served from Redis, not the replica. | Requires careful key alignment; `allEntries` eviction on delete still leaves a window for list queries. |
| **Primary-pinned confirmation read** | After a write, route the confirmation read through a write transaction (`@Transactional` without `readOnly`) so it hits the primary. | Adds primary load; requires the caller to use a distinct "strong read" endpoint or parameter. |
| **Accept eventual consistency** | Document that browse paths are eventually consistent; require strong reads only through write-transaction paths. | Simplest; acceptable if the UI does not display just-written data immediately after redirect. |
| **Synchronous replication** | Configure PostgreSQL synchronous commit to the replica. | Eliminates lag but degrades write latency and availability (replica down blocks writes). Not recommended for catalogue workloads. |

The recommended short-term approach is **write-through cache** (option 1) for
single-entity reads, combined with **accepting eventual consistency** (option 3) for
list/browse queries where populating the cache for every item is impractical.

## Consequences

- Catalogue browse traffic is offloaded from the primary, improving write throughput
  and reducing contention.
- A replica outage is transparent to API consumers (automatic degradation to
  primary).
- Read-after-write consistency is **not** guaranteed in the current implementation.
  Clients that create or update a product and immediately re-fetch it may observe
  stale data. This must be addressed before any flow depends on immediate
  read-your-writes semantics (e.g. a UI redirect to a product detail page after
  creation).
- The staleness window is bounded by PostgreSQL streaming replication lag under
  normal conditions but is theoretically unbounded under partition or replica
  overload.
