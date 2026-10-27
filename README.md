# spring-micro-demo

A runnable Spring Boot reference implementation of a small e-commerce backend,
built to demonstrate microservices architecture principles: service discovery,
centralized configuration, an API gateway with JWT auth, database-per-service,
the saga pattern for distributed transactions, and event-driven notifications.

It also includes a local-LLM feature: `review-service` reads free-text product
reviews with a model running on your own machine (Spring AI + Ollama) and turns
each one into four 0-100 scores, served to a small React scoreboard. No API
keys, no hosted provider, nothing leaves the host.

### Platform versions

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.2 |
| Spring Cloud | 2026.0.0-RC1 |
| Spring AI | 2.0.2 |

Spring Cloud is a **release candidate** here, and deliberately so: its trains
are pinned to a Boot generation, and no GA train targets Boot 4.1 - `2025.1.x`
is built against Boot 4.0.x and `2026.0.x` against Boot 4.2.x. Boot 4.1
therefore has no all-GA Spring Cloud pairing at all, and the version check
that would normally refuse to start is switched off per service
(`spring.cloud.compatibility-verifier.enabled: false`). The whole stack was
verified running on this combination, but if you want an all-released stack,
drop to Boot 4.0.9 + Spring Cloud 2025.1.4 and re-enable the verifier.

## Architecture

Solid lines (`│`) are **synchronous** HTTP; dotted lines (`┆`) are the one
**asynchronous** hop. Every solid edge is a place where the caller's thread waits and
the callee's health becomes the caller's problem; the dotted edge is the one place it
doesn't. See [Synchronous vs. asynchronous](#synchronous-vs-asynchronous-and-why-each-edge-is-what-it-is) for why each edge is which.

Off the request path: every service registers with **discovery-server** (Eureka, 8761)
and pulls its config from **config-server** (8888) at startup.

```
  client
    │ HTTP + JWT
    ▼
 ┌──────────────────────────────────────────────────────────────────────────┐
 │                           api-gateway (8080)                             │
 │   verifies the JWT once, forwards X-User-Id downstream, circuit breaker  │
 │   + fallback per route. Only the paths below exist from outside.         │
 └──┬───────────────┬────────────────┬────────────────┬────────────────┬────┘
    │ /api/auth     │ /api/products  │ /api/carts     │ /api/orders    │ /api/checkout
    │ /api/users    │                │                │                │
    ▼               ▼                ▼                ▼                │
 ┌────────────┐ ┌───────────────┐ ┌──────────────┐ ┌──────────────┐    │
 │user-service│ │product-service│ │ cart-service │ │ order-service│    │
 │   (8084)   │ │    (8081)     │ │    (8085)    │ │    (8082)    │    │
 │   userdb   │ │   productdb   │ │    cartdb    │ │    orderdb   │    │
 └────────────┘ └───────▲───────┘ └──────┬───────┘ └──────▲───────┘    │
                        │                │                │            │
            ① reserve   │     checkout,  │    ② create    │            │
            ⓐ release   │     one cart   │    ④ confirm   │            │
                        │     item at a  │    ⓒ fail      │            │
                        │     time       │                │            │
                        │          ┌─────▼────────────────┴────────────▼────┐
                        └──────────┤       order-orchestrator (8086)        │
                                   │       saga coordinator, no DB          │
                                   └────┬──────────────────────────┬────────┘
                       ③ charge         │                          ┆ ⑤ publish
                       ⓑ refund         │                          ┆ OrderConfirmed /
                                        ▼                          ┆ OrderFailed,
                         ┌──────────────────────┐                  ┆ never waits
                         │   payment-service    │                  ▼
                         │  (8083), internal -  │      ┌──────────────────────┐
                         │  never routed via    │      │    RabbitMQ (5672)   │
                         │     the gateway      │      │ topic "order-events" │
                         │      paymentdb       │      └──────────┬───────────┘
                         └──────────────────────┘                 ┆ binding order.#
                                                                  ▼
                                                       ┌──────────────────────┐
                                                       │ notification-service │
                                                       │  (8087), simulated   │
                                                       │        email         │
                                                       └──────────────────────┘
```

Circled numbers are the saga's happy-path steps and ⓐⓑⓒ its compensations - both are
spelled out under [Checkout data flow](#checkout-data-flow).

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
| `review-service` | 8088 | Owns product reviews and their scores. Reads each review with a local LLM via Spring AI, with a deterministic keyword scorer as fallback. Own DB (`reviewdb`). |
| `frontend` | 5173 | React UI covering every gateway route: catalog, cart, checkout, orders, review scores, account (Vite dev server). Talks only to the gateway. |
| RabbitMQ | 5672 (amqp), 15672 (mgmt UI) | The event bus between order-orchestrator and notification-service. |
| Ollama | 11434 | The local LLM runtime `review-service` calls. Optional - without it, scoring degrades to the fallback scorer. |

### The review scoring path

Kept out of the diagram above because it shares none of the saga's edges. It is
its own slice: one more gateway route, one more service, and one dependency
that happens to be a language model instead of another HTTP service.

```
  browser (React, 5173)
    │  Vite proxies /api  ->  :8080, so requests are same-origin
    ▼
  api-gateway (8080) ── /api/reviews/**, reviewServiceCB, TimeLimiter 70s
    │                   (70s because the callee blocks on CPU inference,
    │                    not because anything here is slow by design)
    ▼
  review-service (8088) ─── reviewdb: review text + the scores derived from it
    │
    ├── Feign ──> product-service        product names for the scoreboard.
    │             (8081)                 Cosmetic: falls back to
    │                                    "Product #2" rather than failing.
    │
    └── Spring AI ChatClient
          │  @CircuitBreaker("reviewScoring"), no retry, 60s read timeout
          ▼
        Ollama (11434) ── llama3.2, structured output bound to ReviewScore
          │
          ╎ unreachable / not pulled / unparseable reply / breaker open
          ▼
        HeuristicReviewScorer ── keyword scoring, instant, deterministic.
                                 Result tagged FALLBACK so the API and the
                                 UI can both say where the number came from.
```

Each review yields `overall`, `quality`, `value` and `reliability` on 0-100,
plus a sentiment label, a one-line summary and a few topics. **50 means the
review never mentioned that aspect** - distinct from a bad score, and the
scoreboard draws that midline explicitly.

## Checkout data flow

The one request that touches nearly everything. `POST /api/carts/me/checkout` with a
bearer token:

```
 client
   │ POST /api/carts/me/checkout   Authorization: Bearer <jwt>
   ▼
 api-gateway ── verifies the JWT, replaces it with X-User-Id downstream
   │            (nothing behind here re-verifies the token)
   │ TimeLimiter 40s  ── cartServiceCB
   ▼
 cart-service ── loops its cart lines, one saga per line, sequentially
   │              budget 20s total; when spent, remaining lines come back
   │              as notAttempted and stay in the cart
   │ Feign, 14s read timeout  (must exceed the 12.5s saga bound below)
   ▼
 order-orchestrator ─── the saga, bounded at 12.5s
   │
   │  ① reserve stock    ──▶ product-service   POST /api/products/{id}/reserve
   │       404 no such product / 409 out of stock ⇒ reject, nothing to undo
   │
   │  ② create order     ──▶ order-service     POST /api/orders        → PENDING
   │       failure ⇒ ⓐ release stock
   │
   │  ③ charge           ──▶ payment-service   POST /api/payments/charge
   │       idempotent in orderId, so ③ is safe to retry
   │       402 declined  ⇒ ⓐ release stock, ⓒ fail order, reject
   │       409 conflict  ⇒ ⓐ release stock, ⓒ fail order, reject
   │       anything else ⇒ ⓐ release stock, ⓒ fail order, 503
   │
   │  ④ confirm order    ──▶ order-service     PATCH /api/orders/{id}/confirm
   │       failure ⇒ ⓐ release stock, ⓑ refund, ⓒ fail order
   │                  (payment already succeeded: two steps to undo)
   │
   │  ⑤ publish OrderConfirmed / OrderFailed ── fire-and-forget; a publish
   │       ┆                                     failure is logged, not raised
   │       ┆   async from here: checkout does not wait, and ⓒ fail order
   │       ┆   publishes OrderFailed the same way
   │       ▼
   │     RabbitMQ  topic exchange "order-events"
   │       ┆   routing key order.confirmed / order.failed, bound by order.#
   │       ▼
   │     notification-service  (simulated email)
   ▼
 per-item results ── success / failure / notAttempted, one per cart line
```

Compensations ⓐ release stock, ⓑ refund, ⓒ fail order are themselves synchronous
Feign calls, retried but **not** circuit-broken — short-circuiting a compensation
would just mean the compensation silently doesn't happen. If one exhausts its
retries it's logged as `COMPENSATION FAILED` and the state is left for manual
reconciliation; an order stuck in `PENDING` is the known residue of that path.

### Synchronous vs. asynchronous, and why each edge is what it is

| Edge | Style | Why |
|---|---|---|
| client → gateway → any service | sync HTTP | A user is waiting for the answer. |
| cart-service → order-orchestrator | sync Feign | Cart needs each line's outcome to decide whether to clear that line or leave it for a retry. |
| orchestrator → product / order / payment | sync Feign | Each step's input is the previous step's output, and a failure at step N has to compensate steps 1..N-1 *now*, while the saga still knows what it did. |
| orchestrator → notification-service | **async** (RabbitMQ) | Nobody needs the result. A notification outage must not fail or slow a checkout. |
| any service → Eureka / config-server | sync HTTP | Startup and lookup plumbing, off the request path. |

**What the sync edges cost:** temporal coupling (payment-service down means checkout
down), latency that adds up across four hops, threads held while waiting, and failures
that cascade unless contained — which is why every one of them carries a timeout, and
the two with real side effects carry a retry and a circuit breaker too. What they buy
is a single readable `CheckoutService` that tells the whole story, immediate
consistency, and a real answer for the user.

**What the async edge costs:** eventual consistency (the user sees "confirmed" before
any email exists), weaker delivery guarantees (`OrderEventPublisher` logs and swallows
publish failures — there's no outbox or publisher confirm, so an event *can* be lost),
a broker to operate, harder end-to-end tracing, and a contract duplicated on both
sides. What it buys is that `notification-service` can be down, slow, or mid-deploy
and checkout neither knows nor cares — and a second consumer can be added with zero
changes to `order-orchestrator`.

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
- **An LLM as an ordinary, fallible dependency** - `review-service` treats a local model the way the saga treats `payment-service`: behind a circuit breaker, with an explicit fallback, a bounded timeout, and provenance recorded on every result. The model is an implementation detail of "score this review" - no endpoint takes a prompt, and swapping `llama3.2` for `qwen2.5` or for the keyword scorer changes nothing about the API contract.

## Prerequisites

- JDK 25
- Maven
- Node.js 20+ and npm (for the React scoreboard)
- Docker + Docker Compose (for RabbitMQ at minimum, even running everything else locally; needed for the full containerized run)
- [Ollama](https://ollama.com/download) — **optional**. Without it `review-service`
  still runs and still scores every review, using its keyword fallback; the UI
  says so plainly rather than pretending otherwise.

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
java -jar review-service/target/review-service-1.0.0.jar
java -jar api-gateway/target/api-gateway-1.0.0.jar
```

Check <http://localhost:8761> to confirm everything shows as `UP`.

Eureka's registry fetch runs on an interval, so for the first ~30 seconds
after `api-gateway` starts it can answer `503` on a route whose service is
already up. That's the load balancer not having seen it yet, not a failure -
wait and retry.

### Start the LLM (optional but it's the point of the feature)

```bash
ollama serve          # usually already running after install
ollama pull llama3.2  # ~2GB, one time
```

`review-service` picks this up with no restart - it resolves the daemon per
call, and the scoreboard's engine lamp re-checks every 15 seconds. Swap the
model in `config-repo/review-service.yml` (`spring.ai.ollama.chat.options.model`);
nothing in the Java code names one.

### Start the React front-end

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. Vite proxies `/api` to the gateway on `:8080`,
so the browser makes same-origin requests and the app exercises the same JWT
filter and circuit breakers as any other client.

It covers every feature reachable through the gateway, one tab each:

| Tab | What it exercises |
|---|---|
| **Catalog** | `product-service` list/create/delete, plus the two ways into the saga — *Add to cart* (batched, one saga per line) and *Buy now* (straight to `/api/checkout`). Low and zero stock are called out. |
| **Cart** | `cart-service` add/remove/checkout. Renders the checkout result as **three** outcomes, not two: placed, failed, and *left in cart* — the last meaning the time budget ran out before that line was attempted, so retrying is safe. |
| **Orders** | `order-service` history with `PENDING`/`CONFIRMED`/`FAILED` and the failure reason. `PENDING` is shown as its own state, because a row stuck there is how you see a saga that died partway. |
| **Review scores** | The LLM feature: engine lamp, per-product aggregates, the reviews behind them, re-scoring, and submitting a new review. |
| **Account** | `user-service` profile, and a plain statement of the trust boundary. |

Two gateway-routed paths are deliberately **not** in the UI:
`/api/products/{id}/reserve|release` and `/api/orders/{id}/confirm|fail`. Those
are individual saga steps meant to be driven by `order-orchestrator` together
with their compensations — a button that moves stock or flips an order's status
with no saga around it would be the kind of completeness that corrupts data.

The scoreboard needs a token, and `user-service` keeps accounts in memory, so
**after any restart create the account again** — the sign-in form has a
"Create this account" button for exactly that.

## Running with Docker Compose

```bash
mvn clean package -DskipTests
docker compose up --build
```

Compose brings everything up in dependency order (including RabbitMQ and
Ollama) using health checks. Pulled models live in the `ollama-models` volume
and survive `docker compose down`.

One manual step, once, after the stack is up — a 2GB download doesn't belong
in a healthcheck:

```bash
docker compose exec ollama ollama pull llama3.2
```

The front-end is not containerized; run it with `npm run dev` as above.

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

### The score features

```bash
# Which scorer is actually live right now
curl http://localhost:8080/api/reviews/engine -H "Authorization: Bearer $TOKEN"

# The scoreboard: one aggregated row per product
curl http://localhost:8080/api/reviews/scores -H "Authorization: Bearer $TOKEN"

# Score a new review (blocks on the model: ~1-5s warm, up to ~30s cold)
curl -X POST http://localhost:8080/api/reviews -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":2,"author":"ravi","text":"Fantastic panel for the money, though the stand wobbles badly."}'

# Re-score text already stored - this is the one to try before and after
# starting ollama, on the same review
curl -X POST http://localhost:8080/api/reviews/1/rescore -H "Authorization: Bearer $TOKEN"
```

Every response carries `scoreSource` (`LLM` or `FALLBACK`), `scoredBy` (the
model id, or `heuristic`) and `scoringMillis`, so you can always tell what
produced a number and what it cost.

The seeded reviews are deliberately scored by the **fallback** at startup: they
arrive with aspect scores stuck at 50 and a boilerplate summary. Re-scoring one
with ollama running replaces them with real analysis of the same text, which is
the clearest way to see what the model actually adds.

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
├── review-service/                  # reviews + LLM scoring via Spring AI (own DB)
├── frontend/                        # React UI over every gateway route
│   ├── src/api.js                   #   the only place that knows the HTTP surface
│   ├── src/views/                   #   one file per tab
│   └── src/components/              #   shared score bars, engine lamp, forms
└── docker-compose.yml
```

## Notable design choices worth reading the code for

- **Orchestration over choreography.** `order-orchestrator` is a single service that calls each participant and knows every compensation, rather than each service reacting to the previous one's event. That means one file (`CheckoutService`) tells the whole story of what happens when payment fails after stock was already reserved - at the cost of that file being the one place that has to know the whole recipe. A choreographed version would spread that knowledge across `product-service`, `order-service`, and `payment-service`'s own event handlers, which is more decoupled but much harder to trace end to end. This tradeoff was made deliberately for this project (see the "orchestration vs. choreography" discussion that shaped this design).
- **Two different resilience tools for two different jobs.** `order-orchestrator`'s calls to `product-service`/`payment-service` use synchronous Resilience4j circuit breakers because the caller needs an answer *now* to decide the next saga step. The `OrderConfirmed`/`OrderFailed` events to `notification-service` go over RabbitMQ instead, because nothing in the checkout path needs to wait for a notification to be sent.
- **`payment-service` is idempotent in `orderId`, because the thing retrying it can't tell success from silence.** A read timeout on `charge` looks identical to a request that never arrived, so `order-orchestrator` retrying it would otherwise charge the order twice. `Payment.orderId` carries a unique constraint and `PaymentService.charge` resolves an existing payment instead of creating a second one - returning the original for a replay, re-raising the same 402 for a replayed decline, and answering 409 when the "replay" isn't one (already refunded, or a different amount). The constraint isn't redundant with the lookup: it's what settles two concurrent retries that both find nothing, and the loser resolves to the winner's row rather than failing the caller. Note that `charge` is deliberately **not** `@Transactional` - the declined path writes a row and then throws, and an enclosing transaction would roll back the very record that makes the decline idempotent.
- **`ResilientProductClient`/`ResilientPaymentClient` are their own Spring beans**, not extra methods on `CheckoutService`. Spring AOP proxies (which is how `@CircuitBreaker`/`@Retry` work) only intercept calls arriving from *outside* the bean - a method calling a sibling method on `this` never goes through the proxy, so the annotations would silently do nothing if collapsed onto one class. This bit us during development; see the git history if you want the "before" that didn't work.
- **`Order` maps to a table named `orders`, and `User` to `users`** - `order` and `user` are both reserved words in H2 (and most SQL dialects) and fail on `CREATE TABLE` otherwise.
- **Feign's default HTTP client can't send PATCH requests** (`java.net.HttpURLConnection` hardcodes the allowed method list). `order-orchestrator` needs PATCH for `confirmOrder`/`failOrder`, so it pulls in `feign-hc5` (Apache HttpClient 5) and enables it via `spring.cloud.openfeign.httpclient.hc5.enabled`. This was `feign-okhttp` + `spring.cloud.openfeign.okhttp.enabled` until **Spring Cloud OpenFeign 5.x removed OkHttp support outright** — only HttpClient5 and Http2Client remain. The upgrade failure mode is the worst kind: the OkHttp property stops being bound, nothing complains, Feign silently falls back to `HttpURLConnection`, and the saga breaks at its *confirm* step — so every checkout charges payment, fails to confirm, compensates, and leaves the order `PENDING`. Caught here only by running a real checkout end to end; no build or startup check sees it.
- **`ignore-exceptions` vs. `ignore-exception-predicate`.** A 404/409 from `product-service` has a dedicated `FeignException` subclass (`NotFound`, `Conflict`), so those are named directly in the breaker config. A 402 from `payment-service` doesn't have one, so `PaymentBusinessOutcomePredicate` (a plain `Predicate<Throwable>`) is used instead - the same intent (don't let a business outcome look like an infrastructure failure), two different mechanisms because Feign only special-cases the more common HTTP statuses.
- **Gateway routes get their own `TimeLimiter` timeout when they hide a saga behind them.** Resilience4j's `TimeLimiter` defaults to 1 second, which is fine for a single-hop route but will spuriously "fail" a checkout call that's actually succeeding, just slowly, several hops deep. `cartServiceCB` and `checkoutCB` are given explicit timeouts for this reason.
- **The timeouts form a ladder, and each rung has to outlast the one below it.** Read bottom-up: `order-orchestrator`'s Feign timeouts and retry counts bound one saga at **12.5s**; `cart-service`'s Feign read-timeout (**14s**) sits above that so it can't abandon a saga that's still running; `cart.checkout-budget` (**20s**) bounds a whole multi-item cart; and the gateway's `cartServiceCB` (**40s**) covers the budget plus one in-flight item. Changing any of these in isolation re-breaks the layer above it - an inner timeout longer than its outer one means the user gets a timeout while work keeps happening behind it, which is exactly how you end up reporting a failure for an order that was actually placed.
- **`cart-service` checks its time budget before starting each item, never mid-call.** Checkout runs cart lines through the saga sequentially, so total time grows with cart size. When the budget is spent, the remaining lines come back as `notAttempted` (distinct from `failure`) and stay in the cart. The distinction matters: nothing was sent downstream for those, so retrying them is unambiguously safe, whereas a genuine `failure` may or may not have left state behind.
- **`spring.cloud.config.retry.max-interval` must be set explicitly whenever `initial-interval` is overridden past resilience4j/spring-retry's own default max, or config-client startup fails outright** with `IllegalArgumentException: Max interval should be > than initial interval` - easy to miss because it doesn't always trigger (it depends on which transitive spring-retry version each module resolves).
- **`payment-service` isn't routed through the gateway at all.** Only `order-orchestrator` calls it, over Eureka/Feign, from inside the trusted network. The gateway's service discovery locator is deliberately disabled (`spring.cloud.gateway.server.webflux.discovery.locator.enabled: false`) so that turning up a new internal service never accidentally exposes it publicly - only routes explicitly listed in `api-gateway.yml` are reachable from outside.
- **Spring AI's default retry policy will hang a request for minutes, and you have to turn it off.** Spring AI wraps every model call in its own `RetryTemplate`, sized for a rate-limited cloud provider: **10 attempts, 2s initial backoff, ×5 multiplier** (2s, 10s, 50s, 250s…). Against a local daemon that simply isn't running, "connection refused" is instant and final, so those defaults turn a 1ms answer into minutes of blocking — long enough that the gateway's `TimeLimiter` fires and the caller gets a `503` instead of the fallback score that was the entire point of having a fallback. `spring.ai.retry.max-attempts: 1` is what makes the fallback actually reachable. This was found by watching a submit take exactly 70s and trip the breaker; it is not obvious from the Spring AI docs.
- **Nothing retries the model, on purpose.** A retry against local inference isn't a cheap re-send like the saga's 250ms HTTP retries — it's another multi-second generation on the same CPU that just failed. One attempt, then the circuit breaker and the deterministic scorer. The breaker is also much more forgiving than the saga's (75% failure rate over a 6-call window) and has slow-call detection disabled, because a cold model load legitimately takes 30 seconds and tripping on that would push every review onto the fallback right when the model was about to become ready.
- **`Review.applyScore` clamps every score, and that's a load-bearing guard, not defensive habit.** A small model asked for 0-100 will occasionally answer `9` or `150`. One such value poisons every average computed from it, so all four scores are clamped at the single chokepoint every scorer goes through — rather than in each scorer, where a future third scorer could skip it.
- **`sentiment` crosses the LLM boundary as a `String`, not an enum.** Structured output binding is strict; a local model answers "Positive", "very positive" or "mixed" often enough that strict enum binding would fail the entire call over a label `Sentiment.parse` maps perfectly well. The lenient parse is at the boundary; the enum is used everywhere inside.
- **The fallback scorer leaves unmentioned aspects at 50 instead of copying the overall score.** 50 means "the review never said", which is genuinely different information from "scored badly" — and the React scoreboard renders that midline explicitly so the distinction survives into the UI. Copying the overall score would have made "arrived dented" silently drag down a value-for-money number the reviewer never commented on.
- **Review text is brace-stripped before it reaches the prompt.** Spring AI renders `{placeholder}` syntax in prompt text, so a customer review containing a stray brace isn't just noise — it can throw during rendering. The system prompt also tells the model to ignore instructions inside the review body.
- **Scoring is not `@Transactional`, and the seeder never calls the model.** Holding a transaction open across a multi-second generation would pin a connection for the whole call and exhaust the pool under a handful of concurrent submits; there's no DB work inside the scoring call, so there's nothing to roll back. Separately, seeding uses the keyword scorer so startup never blocks on inference and works with no ollama installed — which is also what makes the "re-score and watch it change" demo possible.
- **Spring Boot 4 also split the H2 console into its own module.** `spring.h2.console.enabled: true` had been in the config repo all along, but after the upgrade it was an unbound property: services started cleanly and `/h2-console` answered 404. The console now needs `org.springframework.boot:spring-boot-h2console` (one word) on the runtime classpath. Same shape of failure as the gateway properties and the Feign client - Boot 4's modularization turns several "it just worked" features into silent no-ops, and none of them fail the build.
- **Spring Boot 4 dropped `spring-boot-starter-aop`.** Resilience4j's `@CircuitBreaker`/`@Retry` are AspectJ-annotated, so `org.aspectj:aspectjweaver` now has to be requested explicitly (the Boot BOM still manages its version). Resilience4j also ships per-Boot-generation integrations — `resilience4j-spring-boot4`, not `-spring-boot3`.
- **A config-server URL does not belong in `spring.config.import`, and on Boot 4 putting it there breaks Docker.** An import string is a *config-data location*, not an ordinary property: Spring Boot 4 processes the one declared in `application.yml` **in addition to** one supplied via `SPRING_CONFIG_IMPORT`, rather than letting the environment variable replace it. So in a container both get tried — `config-server:8888` (fine) and the leftover `localhost:8888` (nothing listening) — and `spring.cloud.config.fail-fast: true` turns that second, nominally `optional:` import into a hard `ConfigClientFailFastException` at startup. Every service exits 1 while config-server itself sits there healthy, and the stack trace points at `localhost:8888`, which looks like the override never arrived even though `docker compose run ... env` shows it set correctly. The fix is to leave the location bare (`optional:configserver:`) and put the URL in `spring.cloud.config.uri`, which is a normal property with normal override semantics — so `SPRING_CLOUD_CONFIG_URI` in compose works as expected.
- **Spring Cloud Gateway 5.x moved every server property under `spring.cloud.gateway.server.webflux.*`, and the old keys fail silently.** `spring.cloud.gateway.routes` is simply not bound any more. Nothing errors: the gateway starts perfectly clean with zero routes and 404s every request. This is the single most dangerous item in the Boot 4 migration, because a successful startup looks like a successful upgrade.
- **Single-item checkout, multi-item cart.** `order-orchestrator`'s `/api/checkout` takes one product/quantity, matching `order-service`'s current one-product-per-order model. `cart-service` calls it once per cart line and reports per-line results rather than one pass/fail flag - a true multi-item order would need an `OrderItem` entity in `order-service`, which was out of scope for what this saga is trying to demonstrate.
