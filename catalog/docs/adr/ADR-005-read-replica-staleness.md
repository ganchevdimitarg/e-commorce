# ADR-005: Read-replica staleness policy and read-your-writes gap

**Date:** 2026-06-23  
**Status:** Accepted (amended 2026-10-20 — read-your-writes for single-product create/update)

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

### Read-after-write gap: resolved for single-product create/update

The Phase 5 Redis caching layer originally used **cache-aside with eviction on
write**, not write-through population:

- `getProductById` is annotated `@Cacheable(cacheNames = "product", key = "#id")`
  -- it populates the cache on a **read miss** only.
- `createProduct` had **no** cache annotation (no `@CachePut`).
- `updateProduct` and `deleteProduct` used
  `@CacheEvict(cacheNames = "product", key = "#id")` -- they **evicted** the
  product entry on write but did **not** populate it with the new value.

Consequence: immediately after a write, the cache entry for the affected product was
absent. A subsequent `getProductById` call was a cache miss. Because that method is
`@Transactional(readOnly = true)`, the miss was served from the **replica**, which
may not have replicated the write yet. The caller could therefore observe stale data
(the pre-update state, or a 404 for a just-created product).

**Resolved 2026-10-20 by an after-commit cache put on create/update; deletes and
list/browse reads remain eventually consistent** (see Amendment 2026-10-20).

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

### Amendment 2026-10-20

Write-through cache (option 1) is now implemented for single-product create and
update. `ProductCache` (formerly `ProductCacheEvictor`) registers an `afterCommit`
transaction synchronisation that puts the freshly read `ProductResponseDto` into the
`product` cache, so a subsequent `getProductById` is served from Redis rather than a
lagging replica. `updateProduct` re-reads the product inside the write transaction
(primary) to build the cached DTO and returns it. `deleteProduct` still evicts after
commit, and list/browse reads (`getProducts*`, `getProductByName`) remain eventually
consistent.

## Consequences

- Catalogue browse traffic is offloaded from the primary, improving write throughput
  and reducing contention.
- A replica outage is transparent to API consumers (automatic degradation to
  primary).
- Read-your-writes is guaranteed for `getProductById` after `createProduct` /
  `updateProduct`, because the cache is populated after commit. Deletes and
  list/browse reads (`getProducts*`, `getProductByName`) remain eventually
  consistent, bounded by replication lag under normal conditions.
- The staleness window is bounded by PostgreSQL streaming replication lag under
  normal conditions but is theoretically unbounded under partition or replica
  overload.
