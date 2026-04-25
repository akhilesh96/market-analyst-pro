# 📈 Market Analyst Pro: Event-Driven AI Market Dashboard

[![Java](https://img.shields.io/badge/Java-21+-blue)](#)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green)](#)
[![Kafka](https://img.shields.io/badge/Kafka-Event_Driven-black)](#)
[![Redis](https://img.shields.io/badge/Redis-Caching-red)](#)
[![Python](https://img.shields.io/badge/Python-3.10+-yellow)](#)
[![Ollama](https://img.shields.io/badge/Ollama-Llama_3.1-orange)](#)
[![Docker](https://img.shields.io/badge/Docker-Containerized-blue)](#)

An event-driven, full-stack financial analysis engine that ingests NSE stock data via Python, processes it through an Apache Kafka message broker, and utilizes local LLMs (Llama 3.1) via Spring Boot to generate professional, real-time market breakdowns—delivered to a reactive UI without page reloads.

## 🚀 Enterprise-Grade Features

* **Event-Driven Architecture:** Uses Kafka to decouple Python data ingestion from Java-based LLM orchestration, preventing API timeouts and blocking threads.
* **Multi-Tenant State Isolation:** Utilizes `ConcurrentHashMap` and `AtomicBoolean` locks in Spring Boot to process multiple stock tickers simultaneously without data cross-contamination or race conditions.
* **Local Edge AI Inference:** Runs Llama 3.1 8B completely locally via Ollama, ensuring 100% data privacy and zero cloud API costs.
* **Resilient Infrastructure:** Fully containerized with Docker Compose. Includes internal health checks to ensure Spring Boot and Python workers wait for Kafka to achieve a healthy state before connecting.
* **Reactive UI:** Utilizes Server-Sent Events (SSE) to push AI insights directly to a Tailwind-styled frontend with sub-second latency.

## 🧠 System Architecture Workflow

1. **Frontend Request:** User requests an analysis via the UI.
2. **Command Dispatch:** Spring Boot intercepts the REST call and publishes a target ticker to the `stock-command` Kafka topic.
3. **Data Ingestion (Python Worker):** A detached Python worker consumes the command, fetches 5-day historical intraday data (15m candles) via `yfinance`, and streams it back to the `stock-topic`.
4. **AI Orchestration (Java):** Spring Boot consumes the batch data, groups it by ticker in memory, formats an optimized prompt, and queries the local Ollama instance.
5. **Streaming Output:** The final Markdown report is parsed and pushed back to the client via SSE.

## 📂 Project Structure

```text
/market-analyst-pro
├── /backend                 # Spring Boot (Multi-threaded Kafka Consumers, AI Logic)
│   ├── Dockerfile
│   └── src/                 
├── /data-worker             # Python Ingestion Service
│   ├── Dockerfile
│   └── nse_worker.py        
├── docker-compose.yml       # Infrastructure Orchestration (Kafka, Zookeeper, Redis)
└── README.md