# Stock Portfolio Tracker — Design

Date: 2026-08-07
Status: Approved (pre-implementation)

## Purpose

Personal stock portfolio tracker: manual transaction entry, live-ish valuation via free price API, analytics (sector allocation, performance vs benchmark, P&L). Java Spring backend, React frontend. Local-only for now, multi-user via Google OAuth2.

## Scope Assumptions (explicit)

- USD only, US-listed equities only (v1)
- Benchmark comparison = simple % price-curve comparison (portfolio value % change vs SPY % change), no cash-flow-adjusted time-weighted return
- Sector data cached once per symbol (doesn't change often), not re-polled with price
- Deploy target: local Docker-compose only; cloud deploy deferred

## Architecture

```
React (Vite+TS+Tailwind, Chart.js)
   │  REST + JWT (Authorization header)
   ▼
Spring Boot (Maven, layered monolith)
   ├── Security: Spring Security OAuth2 (Google login) → JWT issued on callback
   ├── Controller → Service → Repository (Spring Data JPA)
   ├── Scheduled job: poll Finnhub → PriceSnapshot table
   └── Postgres (users, portfolios, transactions, price_snapshot, symbol_profile)

Docker-compose: postgres + backend + frontend, local only
```

JWT chosen over cookie session: React dev server (:5173) and Spring (:8080) are different origins — JWT via Authorization header avoids CORS/SameSite cookie complexity.

Price API: **Finnhub** (free tier 60 calls/min) — not Alpha Vantage (free tier only 25 calls/day, incompatible with scheduled-poll design). Also provides company-profile endpoint for sector data.

## Milestones

Phased delivery — each milestone runnable/demoable on its own. Each gets its own implementation-plan file via writing-plans skill.

| Milestone | Delivers | Runnable? |
|---|---|---|
| **M1** | Spring+React scaffold, Docker-compose (postgres+backend+frontend), manual portfolio/transaction CRUD, no auth | Yes — enter trades, see computed holdings |
| **M2** | Finnhub scheduled price-poll job + PriceSnapshot cache, live portfolio value/P&L | Yes — real-time-ish valuation |
| **M3** | Analytics: sector allocation (pie), performance vs SPY (line), P&L breakdown (Chart.js) | Yes — dashboard complete |
| **M4** | Google OAuth2 login + JWT, multi-user scoping (portfolios owned by user, all queries scoped by user_id) | Yes — secured, shareable |

## Data Model

```
User          (id, google_sub, email, name, created_at)                    — M4
Portfolio     (id, user_id FK, name, created_at)
Transaction   (id, portfolio_id FK, symbol, type[BUY/SELL], quantity, price, executed_at)
Holding       — NOT a table. Computed on read from Transactions:
                (symbol, quantity = sum(BUY qty - SELL qty), avg_cost_basis)
PriceSnapshot (id, symbol, price, fetched_at)                               — M2
SymbolProfile (symbol PK, sector, industry, name)                          — M3, cached once per symbol
```

Transactions are the source of truth. Holdings are computed on read (not materialized) — avoids sync bugs between a transactions table and a separately-updated holdings table. Revisit materialization only if read-time computation proves too slow (unlikely at personal-project scale).

## API Surface

```
Auth (M4)
  GET  /oauth2/authorization/google        (Spring Security redirect, built-in)
  GET  /login/oauth2/code/google           (callback → issues JWT via custom success handler)

Portfolio (M1)
  GET    /api/portfolios
  POST   /api/portfolios
  GET    /api/portfolios/{id}/holdings     (computed from transactions)
  GET    /api/portfolios/{id}/transactions
  POST   /api/portfolios/{id}/transactions
  DELETE /api/transactions/{id}

Prices (M2)
  GET  /api/prices/{symbol}                (latest cached snapshot)
  (internal) @Scheduled job — no endpoint, writes PriceSnapshot table

Analytics (M3)
  GET  /api/portfolios/{id}/analytics/allocation      (sector % breakdown)
  GET  /api/portfolios/{id}/analytics/performance     (portfolio % change vs SPY % change, time series)
  GET  /api/portfolios/{id}/analytics/pnl             (realized/unrealized gain summary)
```

M1–M3 endpoints unauthenticated (single-user local dev). M4 adds `Authorization: Bearer <jwt>` requirement + scopes all queries by `user_id`.

## Testing Strategy

TDD per `superpowers:test-driven-development` — tests before implementation, each milestone.

| Layer | Tool | Covers |
|---|---|---|
| Backend unit | JUnit5 + Mockito | Service logic: cost-basis calc, P&L math, allocation %, scheduler logic (mocked Finnhub client) |
| Backend integration | Testcontainers (real Postgres) | Repository queries, full controller→DB round trip |
| Frontend unit | Vitest + React Testing Library | Components, hooks (portfolio table, chart data transforms) |
| Frontend e2e | Deferred | Skip for spike; revisit if project grows past personal use |

Finnhub client wrapped behind a `PriceProvider` interface — mocked in tests, real impl wired via Spring config. Keeps test runs from hitting the real API / burning rate limit.

## Claude Code Dev Workflow

**Model selection per task type** — check active model before every task; if it doesn't match, tell user to switch (`/model <name>`) before proceeding:

| Task type | Model | Why |
|---|---|---|
| Boilerplate (entities, DTOs, repos, simple CRUD controllers, basic React components) | Haiku 4.5 | Mechanical, low reasoning need, cheap/fast |
| Core logic (service layer, price-sync scheduler, analytics calc, React hooks/state) | Sonnet 5 (default) | Balanced reasoning |
| High-stakes/tricky (OAuth2+JWT flow, security review, performance/benchmark-math correctness, debugging race conditions in scheduled job) | Opus 5 | Max reasoning, correctness-critical |

**Commit convention:** no `Co-Authored-By: Claude` trailer on commits for this project — plain commits, author only.

**`/clear` cadence:** clear at milestone boundaries only — after a milestone's tasks are all checked off, tests pass, app runs, changes committed. Never mid-milestone.

**Continuation after `/clear`:** implementation plan lives at `docs/superpowers/plans/M<N>-<name>-plan.md` with checkbox tasks — source of truth is that file + git log, not conversation memory. First message after clearing:

> "Continue `docs/superpowers/plans/M<N>-<name>-plan.md` — read it, resume at first unchecked task."

## Deferred / Out of Scope (v1)

- Cloud deployment
- Multi-currency / non-US markets
- Cash-flow-adjusted (time-weighted) return calculation
- Real-time push (WebSocket) — scheduled poll only
- Frontend e2e tests
