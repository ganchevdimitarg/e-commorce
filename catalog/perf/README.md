# Catalog read-path performance baseline

Repeatable baseline for `GET /products/{id}`, `GET /products?q=` and
`GET /products/:cursor`, captured before Phase 2 (tasks 2.1 and 2.2). Re-run this
procedure unchanged to measure those tasks.

## Environment

| Item | Value |
|---|---|
| Captured | 2026-10-22 |
| Branch / commit | `catalog-hardening-2` / `ae1d938` (harness first added in `0399d64`) |
| Host CPU / RAM | Intel Core i7-8565U @ 1.80 GHz / 16 GB |
| Docker VM CPU / RAM | 8 CPUs / 7.68 GiB (`docker info`: `NCPU=8`, `MemTotal=8247701504`) |
| Docker Desktop / engine | Docker Desktop 4.96.0 / Engine 29.9.1 (linux/amd64) |
| k6 image | `grafana/k6:0.54.0` |
| Seed | 10,000 products; products 1-3 have 2,000 comments each |

## Baseline

10 VUs, 45 s, 1 s think time, Redis flushed immediately before the run.

| Endpoint | Request | p50 (ms) | p95 (ms) | p99 (ms) | Median response (bytes) | Failed |
|---|---|---|---|---|---|---|
| by-id, hot product | `GET /api/v1/catalog/products/00000000-0000-0000-0000-000000000001` | 43.95 | 208.52 | 517.42 | 485,040 | 0% |
| search | `GET /api/v1/catalog/products?q=Phone&size=20&sort=createdAt,desc` | 182.08 | 694.43 | 1,210 | 1,461,347 | 0% |
| cursor | `GET /api/v1/catalog/products/:cursor?limit=20&sort=createdAt,desc` | 152.97 | 485.37 | 1,730 | 1,461,569 | 0% |

**This table is one 45 s run, not a stable estimate.** Two runs on this branch and host
(the first predates the pinned search/cursor request shapes in `ae1d938`) differed by
6 % / 20 % / 42 % at p50/p95/p99 for the by-id endpoint, and cursor p99 moved from
586 ms to 1,730 ms. Treat the tail values as order-of-magnitude only: when measuring
tasks 2.1/2.2, run the procedure three times and compare medians (or a stated range),
not one run's p99.

## Procedure

Run from `catalog/`. The perf override disables the 60/min search limit and pins
catalog's expected issuer to the in-network mock URL; k6 runs on the Compose network.

**Shell note.** In Windows PowerShell 5.1, `curl` is an alias for `Invoke-WebRequest`, so
`curl --retry` fails — use `curl.exe` (as below) or Git Bash. In Git Bash, run
`export MSYS_NO_PATHCONV=1` before this block: otherwise MSYS rewrites the
container-side `/tmp/seed.sql` and `/scripts` arguments into Windows paths, breaking the
seed copy and the bind mount. `grep` in step 2 is Git Bash; in PowerShell use
`| Select-String -Pattern 'AUTH_ISSUER_URI|CATALOG_RATE_LIMIT_ENABLED'`.

```bash
# 1. Start the stack with the perf override.
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml up -d --build
curl.exe --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:8088/actuator/health

# 2. Confirm the override reached the container.
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml \
  exec catalog env | grep -E 'AUTH_ISSUER_URI|CATALOG_RATE_LIMIT_ENABLED'
```

Expected:

```text
AUTH_ISSUER_URI=http://mock-oauth2:8080/default
CATALOG_RATE_LIMIT_ENABLED=false
```

```bash
# 3. Seed the standalone database (destructive — see Caveats).
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml \
  cp perf/seed.sql postgres:/tmp/seed.sql
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml \
  exec -T postgres psql -U catalog -d catalog -f /tmp/seed.sql

# 4. Verify the seed before measuring.
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml \
  exec -T postgres psql -U catalog -d catalog -c \
  "SELECT (SELECT count(*) FROM products) AS products,
          (SELECT count(*) FROM comments) AS comments,
          (SELECT count(*) FROM comments
             WHERE product_id = '00000000-0000-0000-0000-000000000001') AS hot_comments;"
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml \
  exec -T postgres psql -U catalog -d catalog -c \
  "SELECT id FROM products ORDER BY created_at DESC LIMIT 3;"
```

Expected: `10000 | 6000 | 2000`; the three newest ids end in `…000000000001`,
`…000000000002` and `…000000000003`, in that order, so the comment-heavy products appear
on page 1 of the sorted search and cursor requests.

```bash
# 5. Flush Redis, then smoke-test 1 VU for 5 s.
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml \
  exec -T redis redis-cli FLUSHDB

docker run --rm --network catalog-standalone_default \
  -v "$(pwd)/perf:/scripts" \
  -e BASE_URL=http://catalog:8084 \
  -e TOKEN_URL=http://mock-oauth2:8080/default/token \
  -e VUS=1 -e DURATION=5s \
  grafana/k6:0.54.0 run /scripts/baseline.js
```

Expected: `token endpoint returned 200` and `token present` at 100 %, every endpoint
check at 100 %, and `http_req_failed: 0.00%`. A 401 means the issuer override is wrong;
a 429 means the rate-limit override is wrong.

```bash
# 6. Flush Redis again and run the measured baseline (10 VUs, 45 s).
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml \
  exec -T redis redis-cli FLUSHDB

docker run --rm --network catalog-standalone_default \
  -v "$(pwd)/perf:/scripts" \
  -e BASE_URL=http://catalog:8084 \
  -e TOKEN_URL=http://mock-oauth2:8080/default/token \
  grafana/k6:0.54.0 run /scripts/baseline.js
```

Expected: the summary prints `by_id_duration`, `search_duration` and `cursor_duration`
with p50/p95/p99 plus the `*_size_bytes` medians, and `http_req_failed: 0.00%`. Run it
three times and record the median (or the range) — see the single-run caveat above.

Teardown (add `-v` to delete the seeded database volume):

```bash
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml down
```

## Caveats

- `seed.sql` is destructive: it truncates `comments`, `products` and `categories`
  in the standalone stack.
- `getProductById` is `@Cacheable("product")`; after the first request the by-id
  latency is the Redis path. The response size still includes all 2,000 comments.
  Redis is flushed before the measured run so run-to-run numbers are comparable.
- Products 1-3 are the newest rows. The search request is sorted explicitly by
  `createdAt,desc` (as is the cursor request), so those three products are guaranteed
  on page 1. That is intentional: it exposes the embedded-comments payload problem
  that task 2.1 fixes.
- Numbers are comparable only with the same host, Docker resources, seed and script.
  Record the environment above and use the same procedure when re-running for 2.1/2.2.

## After task 2.1

Measured 2026-10-22 on `catalog-hardening-2` (working tree with embedded comments removed,
image rebuilt from the same tree), same host, seed, script and procedure as the baseline;
the health probe is now on the management port (8088). Values are the median of three 45 s
runs, each preceded by `FLUSHDB`.

| Endpoint | Request | p50 (ms) | p95 (ms) | p99 (ms) | Median response (bytes) | Failed |
|---|---|---|---|---|---|---|
| by-id, hot product | `GET /api/v1/catalog/products/00000000-0000-0000-0000-000000000001` | 5.56 | 18.65 | 26.17 | 348 | 0% |
| search | `GET /api/v1/catalog/products?q=Phone&size=20&sort=createdAt,desc` | 22.49 | 45.54 | 96.09 | 7,033 | 0% |
| cursor | `GET /api/v1/catalog/products/:cursor?limit=20&sort=createdAt,desc` | 7.84 | 16.22 | 28.48 | 7,255 | 0% |

Per-run spread: by-id p50 4.81–8.94 ms, p99 19.61–104.46 ms; search p50 20.59–25.58 ms,
p99 68.59–230.56 ms; cursor p50 6.86–11.36 ms, p99 22.53–105.64 ms. The first run is the
slowest (warm-up), which the medians above absorb.

The payload collapse is the point: products 1-3 no longer embed 2,000 comments, so the
by-id response drops from 485,040 to 348 bytes and search/cursor from ~1.46 MB to ~7 KB.
Latency falls with the payload; comparisons remain order-of-magnitude given the single-run
caveat above.
