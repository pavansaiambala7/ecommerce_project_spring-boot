# Project Roadmap

Where this project actually is, and what is worth doing next. Updated 2026-11-10.

## Current state

| Layer | What is running |
| --- | --- |
| Backend | Spring Boot 3.2.5, Java 17, 15 REST controllers, 21 services |
| Database | PostgreSQL 16 with pgvector, pg_trgm and tsvector; 16 Flyway migrations, `ddl-auto=validate` |
| Search | Keyword (GIN/tsvector) and vector (HNSW/cosine) results fused with reciprocal rank fusion |
| Frontend | React 18, Vite 5, React Router 6, Material UI 6, served same-origin from the jar |
| Auth | JWT, 15-minute access tokens, 7-day refresh with rotation, refresh tokens stored hashed |
| Payments | Razorpay order creation and webhook, HMAC-SHA256 verified; cash on delivery otherwise |
| Tests | 201 tests, JUnit 5 and Mockito, real PostgreSQL through Testcontainers, JaCoCo report |
| Delivery | GitHub Actions, OIDC role assumption, ECR, deploy to EC2 over Systems Manager |

## Guiding principles

- Keep the current stack direction: Spring Boot JSON API + React single-page app.
- Prefer incremental change over rewrites.
- Migrations own the schema; Hibernate only validates it.
- Behaviour changes ship with tests.
- Main stays deployable.

## Next

Ordered by what a visitor to this repository notices first.

1. **Give the deployment a stable address.** The host is reached by a bare IP, so a
   stop/start or a released Elastic IP breaks every link to the demo, including the
   one in the README. Pin the address, or move to a host that issues a DNS name and
   a TLS certificate.
2. **Add an error boundary around the router.** A render error currently blanks the
   whole page with no message, because nothing above `<Routes>` catches it.
3. **Split the frontend bundle by route.** It is 629 KB raw, 193 KB gzipped, and the
   admin pages are in it for every visitor who will never open them. `React.lazy`
   plus `Suspense` per route is the obvious first cut.
4. **Finish catalogue photography.** Several departments still fall back to the
   placeholder tile. Some have no honest CC0 photograph available, so this ends in a
   documented decision rather than full coverage.
5. **Publish the API.** There is no OpenAPI document; the README's endpoint table is
   maintained by hand and drifts. Adding springdoc generates it from the controllers.

## Later

- `CONTRIBUTING.md`, `SECURITY.md` and issue templates — none exist yet.
- Prometheus metrics. Actuator is present but only `/health` is exposed and
  `micrometer-registry-prometheus` is not on the classpath.
- Caching for read-heavy storefront endpoints, measured before it is added.
- Password reset and email verification, both of which need an email sender.
- Checkstyle in the build and in CI.
- Architecture decision records under `docs/adr/`.

## Already done

These were open items earlier and are now in the codebase: Java 17 and Spring Boot 3
with the `jakarta.*` migration; Flyway migrations; Docker Compose for local runs;
GitHub Actions CI on every push; a global exception handler; pagination and sorting;
full cart and checkout with order states; pessimistic row locking on stock
(`productDao.java:41`) with `Idempotency-Key` support on the write paths; multiple
addresses per user; database-level product search, later extended to hybrid search.

## Not doing

- Rewriting to another backend framework such as Quarkus or Micronaut.
- Rewriting the storefront in another frontend framework such as Vue or Svelte.
- A separate vector database or Elasticsearch. pgvector keeps the embedding on the
  product row, written in the same transaction, so search results cannot reference
  rows that no longer exist. A second store would have to be kept in sync for no
  gain at this catalogue size.
- Splitting into microservices. The monolith is the point: one deployable, one
  database, one transaction boundary around checkout.
