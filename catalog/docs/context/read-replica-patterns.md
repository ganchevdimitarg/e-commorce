# Read-replica patterns

Writer/reader routing via `AbstractRoutingDataSource`, with health-checked graceful degradation.

## DataSourceRouter

Extends `AbstractRoutingDataSource`. `determineCurrentLookupKey()` returns `Route.READER` when the
current transaction is read-only **and** the replica is healthy; otherwise `Route.WRITER`:

```java
public class DataSourceRouter extends AbstractRoutingDataSource {

    public enum Route { WRITER, READER }

    private final BooleanSupplier replicaHealthy;

    public DataSourceRouter(boolean replicaHealthy) {
        this.replicaHealthy = () -> replicaHealthy;
    }

    public DataSourceRouter(BooleanSupplier replicaHealthy) {
        this.replicaHealthy = replicaHealthy;
    }

    Route resolveRoute() {
        boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        if (readOnly && replicaHealthy.getAsBoolean()) {
            return Route.READER;
        }
        return Route.WRITER;   // writes, and the graceful-degradation fallback
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return resolveRoute();
    }
}
```

## HikariCP pools

| Pool | Property | Max conns | Port |
|---|---|---|---|
| Writer | `catalog.datasource.writer.url` | 10 | 5432 |
| Reader | `catalog.datasource.reader.url` | 20 | 5433 |

The reader pool has tighter timeouts: `connectionTimeout=2000`, `validationTimeout=1000`.

## LazyConnectionDataSourceProxy

The primary `@Bean` wraps the router in a `LazyConnectionDataSourceProxy`, deferring the physical
JDBC connection until the first SQL statement executes:

```java
return new LazyConnectionDataSourceProxy(router);
```

## Flyway routing

The writer bean carries `@FlywayDataSource` — Flyway always migrates against the primary, never
the replica.

## Health probing

`cachedHealthProbe()` wraps the raw `Connection.isValid(1)` check in a TTL cache (2 seconds).
A `ReentrantLock.tryLock()` double-check avoids stampede: only one thread probes at a time;
others return the last known result.

```java
private static final long HEALTH_PROBE_TTL_MS = 2_000;

private static BooleanSupplier cachedHealthProbe(DataSource readerDataSource) {
    ReentrantLock probeLock = new ReentrantLock();
    long[] lastCheckTime = {0};
    boolean[] lastResult = {true};
    return () -> {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime[0] >= HEALTH_PROBE_TTL_MS) {
            if (probeLock.tryLock()) { /* probe and update cache */ }
        }
        return lastResult[0];
    };
}
```

## Graceful degradation

If the replica health probe fails, `replicaHealthy` returns `false` and the router falls back
to `Route.WRITER` (which is also the `defaultTargetDataSource`). Read-only queries silently
execute on the primary until the replica recovers.

## Transaction conventions

- `@Transactional(readOnly = true)` routes to the reader pool.
- `@Transactional` (default) routes to the writer pool.
- Service layer only — never on controllers or repositories.

## Staleness trade-offs

PostgreSQL streaming replication introduces sub-second lag under normal conditions. Catalog
mitigates stale reads by evicting the product cache on writes (cache-aside, not write-through).
Critical reads that must see their own writes should use `@Transactional` (not read-only).

## Optimistic locking

All entities extend `Auditable`, which carries a JPA `@Version` column (`V6__add_version_columns.sql`).
Stale updates return zero rows from the versioned `UPDATE ... WHERE version = ?` query;
the service throws `ObjectOptimisticLockingFailureException`, mapped to **409 Conflict**
by `ControllerExceptionHandler`.

Migration history:
- `V1` tables, `V2` indexes, `V3` constraints, `V4` audit columns, `V5` products.stock to boolean, `V6` `@Version` columns.
