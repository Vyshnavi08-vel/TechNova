# Order Processing System with Live Operations Dashboard

Spring Boot + PostgreSQL backend, React dashboard. Concurrent order
processing with a bounded thread pool, pessimistic row locking to prevent
oversold inventory, bounded retry for transient failures, and a persisted
dead-letter queue for permanently failed orders.

## Run it (fastest path)

**1. Start Postgres**
```bash
docker compose up -d
```

**2. Start the backend**
```bash
cd backend
./mvnw spring-boot:run
```
(No `mvnw` wrapper included — if you don't have Maven installed, run
`mvn -N io.takari:maven:wrapper` once inside `backend/` first, or just use
`mvn spring-boot:run` if Maven is already on your PATH.)

Backend comes up on **http://localhost:8080**, creates its schema
automatically (`ddl-auto: update`), and seeds 5 sample products.

**3. Start the frontend**
```bash
cd frontend
npm install
npm run dev
```
Dashboard comes up on **http://localhost:5173**.

## Proving the "never oversell" guarantee

```bash
./concurrency-test.sh
```
Fires 20 concurrent orders for 5 units each of a product seeded with only 5
in stock. You should see exactly one order COMPLETED, the rest FAILED →
DEAD_LETTERED with an "Insufficient stock" reason, and the product's stock
settle at exactly 0 — never negative. Watch it happen live in the dashboard
while the script runs.

## How the hard requirements are met

| Requirement | Where |
|---|---|
| Concurrent processing via thread pool | `config/AsyncConfig.java` — bounded `ThreadPoolTaskExecutor` (4 core / 8 max / 100 queue), orders submitted to it via `@Async` in `OrderProcessingService.processOrderAsync` |
| Locking / concurrency control | `repository/ProductRepository.findByIdForUpdate` uses `@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`, taken inside a single `@Transactional` in `InventoryService.reserveStock`. Rows are locked in ascending product-id order to avoid cross-order deadlocks. A `@Version` column on `Product` is a second line of defense. |
| Never oversell | Stock check-and-decrement happens under the row lock in the same transaction — a second thread waiting on the lock always sees the post-decrement value before it checks |
| Failed / out-of-stock orders | `OutOfStockException` is treated as a **permanent** failure (no point retrying — the stock isn't coming back) and routed straight to the DLQ |
| Dead-letter queue | `model/DeadLetterOrder` + `dead_letter_orders` table, written by `DeadLetterService`. Persisted, not in-memory, so it survives restarts. Viewable and replayable from the dashboard (`POST /api/orders/{id}/replay`) |
| Bounded retry | `OrderProcessingService`: transient failures (lock timeouts, DB blips) get up to `MAX_ATTEMPTS = 3` tries with exponential backoff before falling through to the DLQ. Business failures (out of stock) skip retry entirely. |
| React dashboard | `frontend/` — live order table, inventory table, DLQ panel with replay, polling every 2s |
| Live order status | Dashboard polls `GET /api/orders` every 2s; statuses: `PENDING → PROCESSING → COMPLETED` or `RETRYING → FAILED → DEAD_LETTERED` |
| Live inventory | Dashboard polls `GET /api/inventory` every 2s |
| Persistence | PostgreSQL via Spring Data JPA (`orders`, `order_items`, `products`, `dead_letter_orders` tables). Swap the JDBC URL/credentials in `application.yml` for an AWS RDS endpoint — no code changes needed. |

## API summary

- `POST /api/orders` — submit an order (`{customerName, items:[{productId, quantity}]}`), returns 202 immediately, processes async
- `GET /api/orders` — all orders, newest first
- `GET /api/orders/{id}` — single order
- `GET /api/orders/dead-letter` — DLQ contents
- `POST /api/orders/{id}/replay` — re-submit a dead-lettered order
- `GET /api/inventory` — current stock levels

## Design notes / talking points for your writeup or demo

- **Why pessimistic locking over optimistic here**: under real concurrent
  load (many orders racing for the same low-stock item), optimistic locking
  alone means most competing transactions fail and have to retry from
  scratch, thrashing the DB. Pessimistic locking serializes access to the
  contended row directly, which is a better fit when contention is expected
  to be high on specific "hot" products (a flash-sale item, for example).
- **Why `CallerRunsPolicy` on the executor**: rather than silently dropping
  order-processing tasks once the pool and its queue are both full, the
  submitting thread does the work itself. This is natural back-pressure —
  the system slows down under extreme load instead of losing orders.
- **Why the DLQ is a table, not an in-memory queue**: an in-memory DLQ is
  wiped on every restart, which is unacceptable for money-touching failures
  that need operator follow-up. A table is also directly queryable/
  replayable from the dashboard.
- **Out-of-stock vs transient failures are handled differently on purpose**:
  retrying an out-of-stock order 3 times wastes time and doesn't change the
  outcome — the interesting bounded-retry logic is reserved for genuinely
  transient failures (lock contention, brief DB unavailability).

## If you want to extend it further (optional, time permitting)

- Swap 2s polling for a WebSocket/SSE push for true real-time updates
- Add Spring Retry (`@Retryable`/`@Recover`) instead of the manual loop for a
  more "textbook" bounded-retry implementation
- Add a scheduled job that auto-replays DLQ entries after a cooldown
- Add Testcontainers-based integration tests that fire concurrent requests
  at a real Postgres instance to assert stock never goes negative
