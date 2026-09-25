# spring-micro-demo

A runnable Spring Boot reference implementation of a small e-commerce backend,
built to demonstrate microservices architecture principles: service discovery,
centralized configuration, an API gateway with JWT auth, database-per-service,
the saga pattern for distributed transactions, and event-driven notifications.

## Architecture

```
                                    ┌──────────────────┐
                                    │ discovery-server  │  Eureka registry (8761)
                                    └─────────▲──────────┘
                                              │ register / discover
                                    ┌─────────┴──────────┐
                                    │   config-server     │  centralized config (8888)
                                    └─────────▲──────────┘
                                              │ fetch config at startup
     client ──▶ ┌───────────────┐            │
                │  api-gateway   │  routes, JWT verification, per-route circuit breakers (8080)
                └───────┬────────┘            │
                        │                     │
     ┌──────────────────┼─────────────────────┼───────────────────────┐
     │                  │                     │                       │
┌────┴─────┐    ┌────────┴────────┐   ┌────────┴────────┐    ┌─────────┴────────┐
│user-service│   │ product-service │   │  cart-service   │    │  order-service   │
│  (8084)   │    │     (8081)      │   │     (8085)      │    │     (8082)      │
│  userdb   │    │   productdb     │   │    cartdb       │    │    orderdb      │
└───────────┘    └────────▲────────┘   └────────┬────────┘    └────────▲────────┘
                           │                     │ checkout             │
                           │           ┌─────────▼──────────┐          │
                           └───────────┤ order-orchestrator  ├──────────┘
                                       │  saga coordinator    │
                                       │      (8086)          │
                                       └─────────┬────────────┘
                                                  │ charges
                                       ┌──────────▼──────────┐
                                       │   payment-service    │  (8083), internal only
                                       │     paymentdb         │
                                       └──────────────────────┘
                                                  │ publishes OrderConfirmed/OrderFailed
                                       ┌──────────▼──────────┐
                                       │      RabbitMQ         │  (5672 / 15672 mgmt UI)
                                       └──────────┬──────────┘
                                                  │ consumes
                                       ┌──────────▼──────────┐
                                       │ notification-service │  (8087), simulates emails
                                       └──────────────────────┘
```

| Service | Port | Role |
|---|---|---|
| `discovery-server` | 8761 | Eureka registry - every service registers here and discovers peers by name. |
| `config-server` | 8888 | Serves externalized YAML config per service from `config-server/.../config-repo` (classpath-backed, no git server needed). |
| `api-gateway` | 8080 | Single entry point. Verifies JWTs, routes by path, load-balances via Eureka, applies a circuit breaker + fallback per route. |
| `user-service` | 8084 | Owns accounts; issues JWTs on login. Own DB (`userdb`). |
| `product-service` | 8081 | Owns the product catalog and inventory. Own DB (`productdb`). |
| `cart-service` | 8085 | Owns each user's shopping cart; hands items to order-orchestrator one at a time on checkout. Own DB (`cartdb`). |
| `order-service` | 8082 | Pure data owner for orders (PENDING/CONFIRMED/FAILED) - no calls to any other service. Own DB (`orderdb`). |
| `order-orchestrator` | 8086 | The saga: reserves stock, creates the order, charges payment, confirms the order - with explicit compensations if any step fails. Not exposed publicly except via `/api/checkout`. |
| `payment-service` | 8083 | Simulated payment processing. Internal only - not routed through the gateway; only order-orchestrator calls it. Own DB (`paymentdb`). |
| `notification-service` | 8087 | Consumes `OrderConfirmed`/`OrderFailed` events off RabbitMQ and simulates sending an email. Fully decoupled from the checkout request path. |
| RabbitMQ | 5672 (amqp), 15672 (mgmt UI) | The event bus between order-orchestrator and notification-service. |

## Principles demonstrated

- **Service discovery** (Eureka) - every service resolves its peers by name via `spring-cloud-starter-netflix-eureka-client` and load-balances client-side; nothing hardcodes a host/port.
- **Centralized, externalized configuration** - all config lives in `config-server`'s `config-repo`, not baked into jars.
- **API Gateway with a real security boundary** - `api-gateway` verifies every JWT itself (HMAC-signed by `user-service` at login) and forwards `X-User-Id`/`X-Username` downstream. Every service behind the gateway trusts those headers rather than re-verifying the token - the network behind the gateway is the trust boundary, verification happens once, at the edge. `/api/auth/**` is the one public route.
- **Database per service** - every service owns a private schema; nobody reads another service's tables directly.
- **The saga pattern for distributed transactions** - placing an order touches three services (product, order, payment) with no distributed transaction coordinator. `order-orchestrator` runs the saga explicitly: reserve stock → create order → charge payment → confirm order, with a named compensation for every step that can fail after an earlier one succeeded (see `CheckoutService`, and the "Notable design choices" section below on why this is orchestration rather than choreography).
- **Resilience** (Resilience4j) - the two saga steps with real side effects (`product-service`, `payment-service`) are wrapped in a circuit breaker + retry; business outcomes (404 no such product, 409 out of stock, 402 declined) are explicitly excluded from tripping the breaker or being retried - only infrastructure failures should count.
- **Event-driven decoupling** - `order-orchestrator` publishes `OrderConfirmed`/`OrderFailed` to a RabbitMQ topic exchange and moves on; it never waits on `notification-service`, which can be down, slow, or mid-deploy without affecting checkout.
- **Graceful degradation over cascading failure** - a checkout call never surfaces a raw 500 for a business outcome; declines, out-of-stock, and infrastructure failures each get a distinct, structured response, and a partially-successful cart checkout reports per-item results instead of one pass/fail flag.
- **Independent deployability** - each service is its own Maven module with its own Dockerfile, database, and small hand-copied DTOs for whatever it consumes from a neighbor - no shared model JAR coupling two services' release cycles together.
- **Observability basics** - every service exposes Actuator `health`/`info`/`metrics`/`circuitbreakers`, with circuit breaker state wired into the health indicator.
- **Containerization** - one Dockerfile per service, orchestrated by `docker-compose.yml` with health-check-gated startup order.

## Prerequisites

- JDK 21+
- Maven
- Docker + Docker Compose (for RabbitMQ at minimum, even running everything else locally; needed for the full containerized run)

## Running locally (no Docker, except RabbitMQ)

RabbitMQ is the one piece worth just running in a container even for an
otherwise all-local run:

```bash
docker run -d --name spring-micro-demo-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

Build everything:

```bash
mvn clean package -DskipTests
```

Start services **in this order** (each needs a few seconds to register with
Eureka before the next one that depends on it starts):

```bash
java -jar discovery-server/target/discovery-server-1.0.0.jar
java -jar config-server/target/config-server-1.0.0.jar
java -jar user-service/target/user-service-1.0.0.jar
java -jar product-service/target/product-service-1.0.0.jar
java -jar payment-service/target/payment-service-1.0.0.jar
java -jar order-service/target/order-service-1.0.0.jar
java -jar order-orchestrator/target/order-orchestrator-1.0.0.jar
java -jar cart-service/target/cart-service-1.0.0.jar
java -jar notification-service/target/notification-service-1.0.0.jar
java -jar api-gateway/target/api-gateway-1.0.0.jar
```

Check <http://localhost:8761> to confirm everything shows as `UP`.

## Running with Docker Compose

```bash
mvn clean package -DskipTests
docker compose up --build
```

Compose brings everything up in dependency order (including RabbitMQ) using
health checks.

## Trying it out

Everything goes through the gateway on port 8080. `/api/auth/**` is the only
public route - everything else needs a bearer token.

```bash
# Register and log in
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"hunter22"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"hunter22"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)

# No token -> 401
curl -i http://localhost:8080/api/products

# Browse the catalog
curl http://localhost:8080/api/products -H "Authorization: Bearer $TOKEN"

# Add to cart and check out
curl -X POST http://localhost:8080/api/carts/me/items -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"productId":1,"quantity":2}'
curl -X POST http://localhost:8080/api/carts/me/checkout -H "Authorization: Bearer $TOKEN"

# See the resulting orders
curl "http://localhost:8080/api/orders?userId=1" -H "Authorization: Bearer $TOKEN"
```

### Watching the saga compensate a failure

- **Insufficient stock**: add more of a product than is in stock, then check out - the item stays in the cart with a clear rejection message, and nothing was reserved.
- **Payment declined**: `payment-service` declines any charge over `payment.decline-above-amount` (default $1000) - add enough quantity to cross that, check out, and watch the order end up `FAILED` with the stock automatically released (`GET /api/products/{id}` before and after).
- **Downstream outage**: stop `payment-service` mid-checkout. The first call is slow (connect timeout + retry) and fails; after enough failures the circuit breaker opens and later calls fail *immediately*. Inspect it with `curl http://localhost:8086/actuator/health/circuitBreakers`. Restart `payment-service` and the breaker moves through `HALF_OPEN` back to `CLOSED` on its own.
- **Notifications are decoupled**: stop `notification-service` and place an order anyway - checkout is unaffected. Bring it back and check its logs for the `[EMAIL SIMULATED]` lines (events aren't replayed for a durable queue with no consumer connected during that window in this simple setup, but new events flow the moment it reconnects).

## Project layout

```
spring-micro-demo/
├── pom.xml                        # parent/aggregator (Spring Boot + Spring Cloud BOM)
├── discovery-server/               # Eureka server
├── config-server/
│   └── src/main/resources/config-repo/   # per-service externalized config
├── api-gateway/                    # routing, JWT verification, circuit breakers
├── user-service/                   # accounts + JWT issuance (own DB)
├── product-service/                # catalog/inventory (own DB)
├── cart-service/                   # shopping cart (own DB); calls order-orchestrator
├── order-service/                  # pure order CRUD (own DB); no outbound calls
├── order-orchestrator/             # the saga: coordinates product/order/payment
├── payment-service/                 # simulated payments (own DB); internal only
├── notification-service/            # consumes order events from RabbitMQ
└── docker-compose.yml
```

## Notable design choices worth reading the code for

- **Orchestration over choreography.** `order-orchestrator` is a single service that calls each participant and knows every compensation, rather than each service reacting to the previous one's event. That means one file (`CheckoutService`) tells the whole story of what happens when payment fails after stock was already reserved - at the cost of that file being the one place that has to know the whole recipe. A choreographed version would spread that knowledge across `product-service`, `order-service`, and `payment-service`'s own event handlers, which is more decoupled but much harder to trace end to end. This tradeoff was made deliberately for this project (see the "orchestration vs. choreography" discussion that shaped this design).
- **Two different resilience tools for two different jobs.** `order-orchestrator`'s calls to `product-service`/`payment-service` use synchronous Resilience4j circuit breakers because the caller needs an answer *now* to decide the next saga step. The `OrderConfirmed`/`OrderFailed` events to `notification-service` go over RabbitMQ instead, because nothing in the checkout path needs to wait for a notification to be sent.
- **`ResilientProductClient`/`ResilientPaymentClient` are their own Spring beans**, not extra methods on `CheckoutService`. Spring AOP proxies (which is how `@CircuitBreaker`/`@Retry` work) only intercept calls arriving from *outside* the bean - a method calling a sibling method on `this` never goes through the proxy, so the annotations would silently do nothing if collapsed onto one class. This bit us during development; see the git history if you want the "before" that didn't work.
- **`Order` maps to a table named `orders`, and `User` to `users`** - `order` and `user` are both reserved words in H2 (and most SQL dialects) and fail on `CREATE TABLE` otherwise.
- **Feign's default HTTP client can't send PATCH requests** (`java.net.HttpURLConnection` restricts the method list). `order-orchestrator` needed PATCH for `confirmOrder`/`failOrder`, so it pulls in `feign-okhttp` and enables it via `spring.cloud.openfeign.okhttp.enabled`.
- **`ignore-exceptions` vs. `ignore-exception-predicate`.** A 404/409 from `product-service` has a dedicated `FeignException` subclass (`NotFound`, `Conflict`), so those are named directly in the breaker config. A 402 from `payment-service` doesn't have one, so `PaymentBusinessOutcomePredicate` (a plain `Predicate<Throwable>`) is used instead - the same intent (don't let a business outcome look like an infrastructure failure), two different mechanisms because Feign only special-cases the more common HTTP statuses.
- **Gateway routes get their own `TimeLimiter` timeout when they hide a saga behind them.** Resilience4j's `TimeLimiter` defaults to 1 second, which is fine for a single-hop route but will spuriously "fail" a checkout call that's actually succeeding, just slowly, several hops deep. `cartServiceCB` and `checkoutCB` are given an explicit 8s timeout for this reason.
- **`spring.cloud.config.retry.max-interval` must be set explicitly whenever `initial-interval` is overridden past resilience4j/spring-retry's own default max, or config-client startup fails outright** with `IllegalArgumentException: Max interval should be > than initial interval` - easy to miss because it doesn't always trigger (it depends on which transitive spring-retry version each module resolves).
- **`payment-service` isn't routed through the gateway at all.** Only `order-orchestrator` calls it, over Eureka/Feign, from inside the trusted network. The gateway's service discovery locator is deliberately disabled (`spring.cloud.gateway.discovery.locator.enabled: false`) so that turning up a new internal service never accidentally exposes it publicly - only routes explicitly listed in `api-gateway.yml` are reachable from outside.
- **Single-item checkout, multi-item cart.** `order-orchestrator`'s `/api/checkout` takes one product/quantity, matching `order-service`'s current one-product-per-order model. `cart-service` calls it once per cart line and reports per-line results rather than one pass/fail flag - a true multi-item order would need an `OrderItem` entity in `order-service`, which was out of scope for what this saga is trying to demonstrate.
