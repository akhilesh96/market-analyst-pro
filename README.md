# Market Analyst Pro

A lightweight, high-concurrency stock intelligence engine built as a modular monolith. It generates on-demand AI commentary for completed 15m/30m candles using non-blocking asynchronous processing, in-flight request deduplication, and a multi-tier cache.

## Architecture

- **Modular Monolith**: Java 21 Spring Boot handles API routing, domain logic, and persistence without microservice bloat.
- **Singleflight Deduplication**: `ConcurrentHashMap<CandleKey, CompletableFuture<Void>>` coalesces concurrent cache misses so multiple simultaneous queries for the same candle trigger only 1 AI inference job.
- **Two-Tier Cache**:
    - **L1 In-Memory Cache (RAM)**: 30-second TTL in Java to absorb client polling and shield external APIs from rate limits.
    - **L2 Persistent Cache (PostgreSQL)**: Permanent storage for immutable completed candle summaries keyed by `(symbol, interval, timestamp)`.
- **Asynchronous Non-Blocking API**: Returns `202 Accepted` immediately with cached data; missing summaries process in the background via Java 21 Virtual Threads while the client polls.
- **Bulkhead Pattern**: Caps concurrent local LLM calls (`Semaphore(2)`) to prevent CPU exhaustion.
- **Real-Time Communication**: Standalone Rust service managing WebSocket connections with zero garbage collection overhead.

## Tech Stack

| Layer | Technology | Purpose |
| :--- | :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.3, JPA, Flyway | Core orchestration, singleflight deduplication, REST API |
| **Database** | PostgreSQL 16 | Persistent candle storage and unique constraint caching |
| **Market Data** | Python 3.12, FastAPI, yfinance | OHLCV retrieval and completed candle filtering |
| **AI Inference** | Ollama (`llama3.2:latest`) | Technical price action and sentiment analysis |
| **Chat Service** | Rust, Axum, Tokio | High-concurrency broadcast WebSockets |
| **Frontend** | React 18, Vite | Minimalist vertical timeline UI and polling |

## Project Structure

```text
market-analyst-pro/
├── backend/               # Java 21 Spring Boot modular monolith
├── frontend/              # React 18 single-column timeline UI
├── market-data-service/   # Python FastAPI + yfinance bridge
├── market-chat-service/   # Rust Axum + Tokio WebSocket server
└── docker-compose.yml     # Infrastructure (Postgres, Python bridge)
```

## Getting Started

### 1. Start Infrastructure
```bash
docker compose up -d postgres market-data-service
```

### 2. Run Backend (Java 21)
```bash
cd backend
mvn spring-boot:run
```

### 3. Run Real-Time Chat (Rust)
```bash
cd market-chat-service
cargo run
```

### 4. Run Frontend (React)
```bash
cd frontend
npm install
npm run dev
```

Dashboard runs at `http://localhost:5173`.

## Automated Testing

```bash
# Run Java integration tests (Testcontainers Postgres + JUnit 5 + Awaitility)
cd backend && mvn test

# Run Python market data unit tests
cd market-data-service && pytest -v
```
## Demo

### 1. On-Demand 30m Stock Analysis & Caching

<img width="1012" height="531" alt="stock_analysis_demo" src="https://github.com/user-attachments/assets/c900031c-458d-4849-9f58-8d8944c8669d" />
### 2. High-Concurrency Rust WebSocket Live Chat

<img width="1012" height="531" alt="live_chat_demo" src="https://github.com/user-attachments/assets/4dc6a092-a477-418e-b261-ef0417eff8bc" />

