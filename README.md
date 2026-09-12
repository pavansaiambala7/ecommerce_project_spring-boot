# E-Commerce Platform (Spring Boot)

A Spring Boot e-commerce application with a server-rendered JSP storefront, a
JWT-secured REST API, and optional AI product search and support chat backed by
Google Gemini and pgvector.

**Java 17 · Spring Boot 3.2.5 · PostgreSQL 16 + pgvector · Flyway · LangChain4j**

---

## Running it locally

### Docker Compose

```bash
export JWT_SECRET="$(openssl rand -base64 48)"   # required
export GEMINI_API_KEY="your-gemini-api-key"      # optional, AI features only
docker compose up --build
```

The application starts on <http://localhost:8080>. Compose will refuse to start
without `JWT_SECRET`; see [Configuration](#configuration).

### Without Docker

You need a JDK 17 and a PostgreSQL 16 with the `vector` and `pg_trgm`
extensions available. The `pgvector/pgvector:pg16` image is the easiest source:

```bash
docker run -d --name ecommerce-db -p 5432:5432 \
  -e POSTGRES_DB=ecommjava -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  pgvector/pgvector:pg16

export JWT_SECRET="$(openssl rand -base64 48)"
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway creates and migrates the schema on startup. The `dev` profile enables SQL
logging and supplies a throwaway JWT secret if you have not set one.

### Demo accounts

Migrations seed two accounts for local exploration:

| Username | Password | Role         |
|----------|----------|--------------|
| `admin`  | `123`    | `ROLE_ADMIN` |
| `lisa`   | `765`    | `ROLE_NORMAL`|

**These are well-known credentials in a file that runs in every environment.**
Delete or rotate them before exposing this application to anything real.

---

## Configuration

Everything below is read from the environment. Only `JWT_SECRET` is mandatory.

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | *(none — startup fails)* | HMAC-SHA signing key, minimum 32 bytes |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/ecommjava` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Database password |
| `GEMINI_API_KEY` | *(empty)* | Enables AI search and chat |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | Comma-separated allow-list |
| `RATELIMIT_TRUST_XFF` | `false` | Trust `X-Forwarded-For` for rate-limit identity |

The application deliberately has **no default signing secret**. A fallback value
committed to source control is equivalent to having no authentication at all, so
it refuses to start rather than come up insecure.

Set `RATELIMIT_TRUST_XFF=true` only when a proxy you control overwrites the
header. If callers can set it themselves, they can present a fresh identity on
every request and the rate limiter stops doing anything.

---

## Authentication

The REST API is stateless and authenticates with JWT bearer tokens. The JSP
pages use ordinary session form-login and are unaffected.

```bash
# Sign in
curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123"}'

# Use the access token
curl -s localhost:8080/api/cart -H "Authorization: Bearer $ACCESS_TOKEN"
```

Access tokens last 15 minutes. Refresh tokens last 7 days, are stored only as a
SHA-256 hash, and rotate on every use — so a stolen refresh token is usable at
most once before the legitimate client's next refresh invalidates it.
`POST /api/auth/logout` revokes one token; `/api/auth/logout-all` revokes every
session for the caller.

---

## REST API

`—` means no authentication required.

### Auth
| Method | Path | Access |
|---|---|---|
| POST | `/api/auth/login` | — |
| POST | `/api/auth/register` | — |
| POST | `/api/auth/refresh` | — |
| POST | `/api/auth/logout` | authenticated |
| POST | `/api/auth/logout-all` | authenticated |

### Products
| Method | Path | Access |
|---|---|---|
| GET | `/api/products`, `/api/products/paged`, `/api/products/{id}` | — |
| POST | `/api/products` | admin |
| PUT | `/api/products/{id}` | admin |
| DELETE | `/api/products/{id}` | admin |

`/api/products/paged` accepts `page`, `size` (1–100), `sortBy`
(`id`, `name`, `price`, `quantity`, `weight`) and `direction`.

### Cart
| Method | Path | Access |
|---|---|---|
| GET | `/api/cart` | authenticated (own cart) |
| POST | `/api/cart/items` | authenticated |
| PUT | `/api/cart/items/{productId}` | authenticated |
| DELETE | `/api/cart/items/{productId}` | authenticated |
| DELETE | `/api/cart` | authenticated |
| POST | `/api/cart/checkout` | authenticated |

### Orders and payments
| Method | Path | Access |
|---|---|---|
| POST | `/api/orders` | authenticated (billed to the caller) |
| GET | `/api/orders/me` | authenticated |
| GET | `/api/orders/{id}` | owner or admin |
| GET | `/api/orders/user/{userId}` | self or admin |
| POST | `/api/orders/{id}/cancel` | owner or admin |
| PATCH | `/api/orders/{id}/status` | admin |
| POST | `/api/payments` | owner of the order |
| GET | `/api/payments/order/{orderId}` | owner or admin |
| POST | `/api/payments/refund/{orderId}` | admin |

### Users
| Method | Path | Access |
|---|---|---|
| GET | `/api/users` | admin |
| GET | `/api/users/me` | authenticated |
| GET | `/api/users/{id}` | self or admin |
| PUT | `/api/users/{id}` | self or admin |

### AI
| Method | Path | Access |
|---|---|---|
| GET | `/api/search?q=…&limit=…` | — |
| POST | `/api/search/reindex` | admin |
| POST | `/api/chat` | authenticated |
| DELETE | `/api/chat/history/{sessionId}` | authenticated |

Every response uses the same envelope:

```json
{ "success": true, "message": "Success", "data": {}, "timestamp": "..." }
```

Validation failures add a `errors` object keyed by field name.

---

## Rate limiting

A Bucket4j token-bucket filter runs **ahead of Spring Security**, so throttled
traffic costs no authentication work and the login endpoints are themselves
protected against credential stuffing. Buckets are keyed by client address and
held in a bounded, expiring cache.

| Path | Limit |
|---|---|
| `/api/chat/**` | 10 / minute |
| `/api/search/reindex` | 2 / hour |
| `/api/search/**` | 30 / minute |
| login, register, refresh | 5 / minute |
| `/api/**` | 100 / minute |

Responses carry `X-RateLimit-Limit` and `X-RateLimit-Remaining`; a `429` also
carries `Retry-After`. Tiers are configurable under `app.ratelimit.tiers`.

---

## Architecture

This is a **single Spring Boot application**, not a set of microservices. It is
organised into four domains — product, user, order and payment — each with its
own service, DAO and API controller, plus a cart and an AI package.

```
controller/        JSP controllers (admin, storefront, cart)
controller/api/    REST controllers
dto/               Request and response DTOs; entities are never serialised
security/          JWT issuing and verification, principal, auth service
ratelimit/         Bucket4j filter and configuration
exception/         Domain exceptions and the global handlers
services/ dao/     Business logic and Spring Data repositories
models/            JPA entities
ai/                Gemini embeddings, pgvector RAG search, support chat
resources/db/      Flyway migrations V1–V7
```

Money is `BigDecimal` throughout, stored as `numeric(12,2)`.

Entities are never returned from a controller. Every endpoint maps to a DTO,
which is what keeps password hashes out of responses and breaks the
`Order → OrderItem → Order` cycle that would otherwise make Jackson recurse.

`spring.jpa.open-in-view` is disabled. Read paths that render an association
fetch it explicitly with `@EntityGraph` rather than lazily loading it during
view rendering.

---

## Testing

```bash
./mvnw verify
```

Tests use JUnit 5, Mockito and `@WebMvcTest` slices, with JaCoCo coverage
written to `target/site/jacoco/`.

Integration tests need a PostgreSQL with pgvector. By default they start one via
Testcontainers; if `SPRING_DATASOURCE_URL` is set, that database is used instead,
which is useful when the build itself runs inside a container.

`JtSpringProjectApplicationTests` boots the whole application, so it exercises
every Flyway migration and Hibernate's schema validation against the result.

---

## AI features

Both AI features are optional and inert without `GEMINI_API_KEY`.

- **RAG product search** embeds the query with Gemini, searches pgvector by
  cosine similarity, and returns ranked products with scores.
- **Support chat** augments the conversation with retrieved product and order
  context. Sessions are namespaced per user and held in a bounded cache.

`POST /api/search/reindex` rebuilds the embedding store. It clears existing
vectors first, and is admin-only and heavily rate-limited because a reindex
issues one paid embedding request per product.

> The configured model names (`gemini-embedding-001`, `gemini-2.0-flash`) should
> be verified against your API access. Note that `pgvector.dimension` must match
> the `vector(...)` width in migration `V2`; changing the embedding model may
> require changing both together.

---

## CI

`.github/workflows/ci.yml` builds and tests on every push and pull request to
`main`, uploading JaCoCo coverage and surefire reports. `Jenkinsfile` provides an
equivalent pipeline for Jenkins.
