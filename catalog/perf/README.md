# Catalog read-path performance baseline

Repeatable baseline for `GET /products/{id}`, `GET /products?q=` and
`GET /products/:cursor`, captured before Phase 2 (tasks 2.1 and 2.2). Re-run this
procedure unchanged to measure those tasks.

## Environment

| Item | Value |
|---|---|
| Captured | 2026-10-22 |
| Branch / commit | `catalog-hardening-2` / `26ff611` |
| Host CPU / RAM | Intel Core i7-8565U @ 1.80 GHz / 16 GB |
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

## Procedure

Run from `catalog/`. The perf override disables the 60/min search limit and pins
catalog's expected issuer to the in-network mock URL; k6 runs on the Compose network.

```bash
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml up -d --build
curl --retry 30 --retry-delay 2 --retry-connrefused -fsS http://localhost:8084/actuator/health

docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml \
  cp perf/seed.sql postgres:/tmp/seed.sql
docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml \
  exec -T postgres psql -U catalog -d catalog -f /tmp/seed.sql

docker compose -f compose-standalone.yaml -f perf/compose.perf.yaml \
  exec -T redis redis-cli FLUSHDB

# PowerShell; Git Bash uses "$(pwd)/perf:/scripts".
docker run --rm --network catalog-standalone_default \
  -v "${PWD}/perf:/scripts" \
  -e BASE_URL=http://catalog:8084 \
  -e TOKEN_URL=http://mock-oauth2:8080/default/token \
  grafana/k6:0.54.0 run /scripts/baseline.js
```

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
