# CoinStream: Real-Time Financial Analytics Platform

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.6-green)
![Kafka](https://img.shields.io/badge/Apache_Kafka-3.7-black)
![React](https://img.shields.io/badge/React-19-blue)
![Tailwind](https://img.shields.io/badge/Tailwind_CSS-4.0-cyan)

CoinStream is a high-frequency trading simulation and analytics platform built with **Event-Driven Microservices**. It demonstrates end-to-end real-time data processing, from ingestion to visualization, capable of handling thousands of events per second with sub-second latency.

## Key Features

*   **Real-Time Ingestion**: Simulates exchange connectivity (e.g., Binance) producing high-frequency tick data.
*   **Stream Processing**: Uses **Kafka Streams** to perform stateful windowed aggregation (1-minute OHLC candlesticks + Simple Moving Average).
*   **Event-Driven Gateway**: Spring Boot API Gateway consuming multiple Kafka topics and broadcasting via **STOMP WebSockets**.
*   **Interactive UI**: Modern React dashboard with **Recharts** visualization, featuring live candlestick rendering and dynamic indicators.
*   **Robustness**: Implements "manual deserialization" patterns to ensure schema evolution resilience between microservices.
*   **Modernized Architecture**: Fully upgraded to Java 25, Spring Boot 4, and Jackson 3. Leverages **Virtual Threads** for high-throughput I/O, Java **Records** for immutable DTOs, and **Micrometer Tracing** for distributed observability.

## Technology Stack

*   **Backend**: Java 25, Spring Boot 4.0.6, Spring Cloud Stream
*   **Messaging**: Apache Kafka, Zookeeper
*   **Frontend**: React 19, Vite, TailwindCSS v4, Recharts, Lucide
*   **Infrastructure**: Docker, Docker Compose

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
