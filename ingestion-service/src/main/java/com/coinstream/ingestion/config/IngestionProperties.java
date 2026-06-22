package com.coinstream.ingestion.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe, immutable binding of the {@code ingestion.*} configuration,
 * replacing scattered {@code @Value} field injection in the producer and the
 * Binance adapter.
 */
@ConfigurationProperties(prefix = "ingestion")
public record IngestionProperties(
        String pricesTopic,
        Binance binance
) {
    public record Binance(String url, Duration reconnectDelay) {}
}
