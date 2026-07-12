# Project layout

Multi-module Spring Boot monorepo (`com.ganchevdimitarg:e-commerce`), Boot 4.1.0 /
Java 25. One Maven reactor, nine modules.

```
/
+-- CLAUDE.md                    <- master context (imports docs/context/* on demand)
+-- pom.xml                      <- parent + BOM (all version pins live here)
+-- docker-compose.yml           <- local infra (PG, Mongo, Redis, Kafka, Vault, Zipkin)
+-- Jenkinsfile                  <- CI (catalog pipeline; fleet rollout pending)
+-- decisions.md                 <- root architectural/technical decision log
+-- docs/
|   +-- decisions.md             <- cross-module decision log (module-local ones live in <module>/decisions.md)
|   +-- adr/                     <- Architecture Decision Records
|   +-- context/                 <- on-demand pattern files (@import targets)
|   +-- sagas/                   <- cross-service flow docs
|   +-- spec/                    <- reviews and specifications
+-- gateway/                     <- WebFlux edge (routing, OAuth2, rate limit)
+-- authentication/              <- OAuth2 authorization server (PG, outbox)
+-- catalog/                     <- products/categories/comments (PG, replica, reference module)
+-- order/                       <- order lifecycle (PG, saga orchestration)
+-- payment/                     <- Stripe payments (PG, idempotent replay)
+-- profile/                     <- customer profiles (MongoDB)
+-- notification/                <- email dispatch (PG, Kafka consumer)
+-- client/                      <- shared opaque-token introspection library
+-- eureka-server/               <- service discovery
+-- .claude/                     <- canonical Claude config (agents, skills, hooks, context)
```

Each service module has its own thin `CLAUDE.md` (identity + deltas). Every module owns
its schema; Flyway migrations live in `<module>/src/main/resources/db/migration/`.
