# 📈 Market Analyst Pro: Real-Time AI Market Dashboard
![Java](https://img.shields.io/badge/Java-17+-blue) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.0-green) ![Kafka](https://img.shields.io/badge/Kafka-Event_Driven-black) ![Python](https://img.shields.io/badge/Python-3.10+-yellow) ![Llama](https://img.shields.io/badge/Ollama-Llama_3.1-orange)

An event-driven, full-stack financial analysis engine that ingests NSE stock data via Python, processes it through an Apache Kafka message broker, and utilizes local LLMs (Llama 3.1) via Spring Boot to generate professional, real-time market breakdowns—delivered to a reactive UI without page reloads.

## 🚀 Key Features

* **Event-Driven Architecture:** Uses Kafka to decouple Python data ingestion from Java-based LLM orchestration.
* **Local Edge AI Inference:** Runs Llama 3.1 8B (Q2_K quantization) completely locally via Ollama, ensuring 100% data privacy and zero cloud API costs.
* **Reactive Frontend:** Utilizes Server-Sent Events (SSE) to push AI insights directly to a modern, Tailwind-styled UI with sub-second latency.
* **Idempotent State Management:** Automatically manages LLM context windows to prevent data hallucinations across multiple stock tickers.
* **Resilient System Design:** Includes automated health checks for local inference engines, providing graceful degradation and UI feedback when downstream AI services are unavailable.

## 🧠 System Architecture

1. **Frontend:** User requests an analysis via the modern Tailwind UI.
2. **Controller Layer:** Spring Boot intercepts the request and publishes a `START` command to a Kafka topic.
3. **Data Ingestion (Python):** A detached Python worker listens for commands, fetches full-day intraday data (15m candles) via `yfinance`, and streams it back to a data topic.
4. **AI Orchestration (Java):** Spring Boot consumes the batch data, formats an optimized prompt, and queries the local Ollama instance.
5. **Streaming Output:** The final Markdown report is parsed and pushed back to the client via SSE.

## 📂 Project Structure

```text
/market-analyst-pro
├── /backend                 # Spring Boot application (Controllers, Services, Kafka Consumers)
│   ├── src/main/java        # Java source code
│   └── src/main/resources   # application.properties & index.html (Tailwind UI)
├── /data-worker             # Python ingestion service
│   ├── nse_worker.py        # yfinance fetcher and Kafka producer
│   └── requirements.txt     # Python dependencies
└── README.md                # Project documentation
🛠️ Prerequisites
To run this project locally, you will need:

Java 17+ and Maven

Python 3.10+ (with yfinance and kafka-python)

Apache Kafka & Zookeeper running on default ports (9092 / 2181)

Ollama installed locally with the llama3.1:8b-instruct-q2_K model pulled.

🏃‍♂️ Quick Start
1. Start Kafka and Zookeeper
(Open PowerShell/Terminal and run your Kafka environment startup scripts)

Bash
# Example for Windows:
.\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties
.\bin\windows\kafka-server-start.bat .\config\server.properties
2. Start the Python Worker
Open a new terminal window, navigate to the python directory, and start the listener:

Bash
cd data-worker
pip install -r requirements.txt
python nse_worker.py
3. Run the Spring Boot Application
Open a new terminal window and run the backend server:

Bash
cd backend
mvn spring-boot:run
4. Access the Dashboard

Ensure Ollama is running in your system tray.

Navigate to http://localhost:8080/ in your browser.

Enter an NSE ticker (e.g., RELIANCE, TCS, HDFCBANK) and click Run Deep Analysis.