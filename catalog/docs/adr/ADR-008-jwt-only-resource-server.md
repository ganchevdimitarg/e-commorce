# ADR-008: JWT-only resource server; no opaque-token introspection in catalog

**Date:** 2026-10-21
**Status:** Accepted

## Context

Catalog previously depended on the shared `client` module's opaque-token
introspector. Every opaque-token request called Google/GitHub/Facebook
synchronously, there was no cache, and an unrecognised token fell through to
Facebook. TODO 2.2 proposed caching the introspector; TODO 7.1 removed the
dependency instead, because catalog is a downstream service and the gateway owns
the social-login contract.

## Decision

Catalog validates only JWTs issued by the auth service (issuer `:8082`) through
`spring-security-oauth2-jose`. The default `scope` claim becomes `SCOPE_*`
authorities; catalog preserves `ROLE_*` values from the same claim. There is no
outbound introspection and therefore no token cache. Social opaque tokens
terminate at auth/gateway.

## Consequences

- No synchronous identity-provider calls and no token-cache infrastructure in
  catalog.
- A social login must be exchanged for an auth-service JWT at the edge before
  catalog is called.
- Catalog can be reached directly on :8084; it validates the JWT locally and
  never trusts `X-User-Id`/`X-User-Roles` headers for identity.

## Alternatives rejected

- **Cached opaque-token introspector (TODO 2.2).** It adds outbound calls and a
  cache for a token type catalog no longer accepts.
- **Keeping the `client` dependency.** It couples catalog to the monorepo and
  duplicates identity logic that belongs at the gateway.
