# CoinStream: Real-Time Financial Analytics Platform

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.6-green)
![Kafka](https://img.shields.io/badge/Apache_Kafka-3.7-black)
![React](https://img.shields.io/badge/React-19-blue)
![Tailwind](https://img.shields.io/badge/Tailwind_CSS-4.0-cyan)

CoinStream is a real-time market-data analytics platform built with **event-driven microservices**. It demonstrates end-to-end streaming — ingestion → stateful stream processing → live visualization — over Apache Kafka. (Throughput and latency figures are deliberately omitted until measured under load; the benchmark is a planned milestone — see the [upgrade plan](docs/EXECUTION-PLAN.md).)

## Key Features

*   **Real-Time Ingestion**: Simulates exchange connectivity (e.g., Binance) producing high-frequency tick data.
*   **Stream Processing**: Uses **Kafka Streams** to perform stateful windowed aggregation (1-minute OHLC candlesticks + Simple Moving Average).
*   **Event-Driven Gateway**: Spring Boot API Gateway consuming multiple Kafka topics and broadcasting via **STOMP WebSockets**.
*   **Interactive UI**: Modern React dashboard with **Recharts** visualization, featuring live candlestick rendering and dynamic indicators.
*   **Service decoupling**: Each service owns its own copy of the message DTOs and deserializes manually, avoiding a shared-model jar that would couple deploy cycles. A documented JSON contract with tolerant readers is the planned next step (see roadmap).
*   **Modernized Architecture**: Fully upgraded to Java 25, Spring Boot 4, and Jackson 3. Leverages **Virtual Threads** for high-throughput I/O, Java **Records** for immutable DTOs, and **Micrometer Tracing** for distributed observability.

## Technology Stack

*   **Backend**: Java 25, Spring Boot 4.0.6, Spring for Apache Kafka, Kafka Streams
*   **Messaging**: Apache Kafka (KRaft mode — no ZooKeeper)
*   **Frontend**: React 19, Vite, TailwindCSS v4, Recharts, Lucide
*   **Infrastructure**: Docker, Docker Compose

## Design Decisions & Tradeoffs

Choices made deliberately — with their tradeoffs stated — rather than by default:

*   **Windows emit on every update, not on close.** The analytics topology intentionally does *not* use `suppress()` / `ON_WINDOW_CLOSE`. Candles update live as ticks arrive, which is the desired UX for a streaming chart. The cost: downstream consumers see multiple emissions per window and must dedupe by `windowStart` (the frontend does exactly this). A use case needing one authoritative record per window (e.g. billing) would use `suppress()` instead.
*   **`BigDecimal` at the boundary, `double` inside aggregation.** Prices are parsed as `BigDecimal` at ingestion to avoid floating-point parse error, then aggregated as `double` in the Kafka Streams topology for performance and serde simplicity. This is acceptable because the output is *analytics* (moving averages, OHLC for display), not order execution or settlement — sub-cent rounding in a displayed average is harmless. A money-movement path would keep `BigDecimal` end-to-end.

## Quick Start

The entire stack is containerized. You can launch the full environment with a single command.

### Prerequisites
*   Docker & Docker Compose

### Launch
```bash
docker-compose up --build
```

Access the application:
*   **Frontend Dashboard**: [http://localhost](http://localhost) (Port 80)
*   **Kafka UI**: [http://localhost:8090](http://localhost:8090)

## Testing

The project includes unit and integration tests for the topology logic.

```bash
cd analytics-service
mvn test
```
